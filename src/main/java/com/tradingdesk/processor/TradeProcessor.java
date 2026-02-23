package com.tradingdesk.processor;

import com.tradingdesk.db.DatabaseManager;
import com.tradingdesk.model.Trade;
import com.tradingdesk.portfolio.PortfolioStateManager;
import com.tradingdesk.validation.TradeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-throughput trade processor that executes trades concurrently using a
 * fixed thread pool.
 *
 * <p>Thread-safety guarantees:
 * <ul>
 *   <li>{@link PortfolioStateManager} uses a {@link ConcurrentHashMap} for
 *       structural changes and {@code synchronized} blocks inside
 *       {@link com.tradingdesk.model.Position} for quantity mutations.</li>
 *   <li>{@link DatabaseManager} synchronizes every write on {@code this}.</li>
 *   <li>Accepted trades are double-checked: SELL trades verify the portfolio
 *       has sufficient quantity before accepting.</li>
 * </ul>
 */
public class TradeProcessor {

    private static final Logger log = LoggerFactory.getLogger(TradeProcessor.class);

    /* Tunable — use Runtime cores for I/O-bound DB work */
    private static final int THREAD_POOL_SIZE =
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    private final DatabaseManager      dbManager;
    private final PortfolioStateManager portfolioManager;
    private final ExecutorService       executor;

    /* Metrics counters */
    private final LongAdder acceptedCount = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();
    private final LongAdder errorCount    = new LongAdder();

    public TradeProcessor(DatabaseManager dbManager, PortfolioStateManager portfolioManager) {
        this.dbManager       = dbManager;
        this.portfolioManager = portfolioManager;
        this.executor = Executors.newFixedThreadPool(
                THREAD_POOL_SIZE,
                new NamedThreadFactory("trade-worker"));
        log.info("TradeProcessor initialized with {} worker threads", THREAD_POOL_SIZE);
    }

    /* ------------------------------------------------------------------ */
    /*  Public interface                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Submits all trades for concurrent processing and waits until every
     * trade has been handled (or the timeout of 60 s elapses).
     *
     * @return number of trades that completed within the timeout
     */
    public ProcessingResult processAll(List<Trade> trades) throws InterruptedException {
        log.info("Submitting {} trades to thread pool …", trades.size());
        long start = System.currentTimeMillis();

        // Ensure account rows exist before any trade references them
        ensureAccounts(trades);

        List<Future<?>> futures = trades.stream()
                .map(trade -> (Future<?>) executor.submit(() -> processSingle(trade)))
                .collect(java.util.stream.Collectors.toList());

        // Wait for all futures
        int completed = 0;
        for (Future<?> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
                completed++;
            } catch (ExecutionException e) {
                log.error("Trade execution error: {}", e.getCause().getMessage());
                errorCount.increment();
            } catch (TimeoutException e) {
                log.error("Trade processing timed out");
                errorCount.increment();
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Processing complete in {}ms — Accepted: {}, Rejected: {}, Errors: {}",
                elapsed, acceptedCount.sum(), rejectedCount.sum(), errorCount.sum());

        return new ProcessingResult(
                trades.size(), completed,
                acceptedCount.sum(), rejectedCount.sum(),
                errorCount.sum(), elapsed);
    }

    /** Gracefully shuts down the thread pool. */
    public void shutdown() {
        log.info("Shutting down trade processor …");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Trade processor shut down.");
    }

    /* ------------------------------------------------------------------ */
    /*  Private – single-trade processing pipeline                          */
    /* ------------------------------------------------------------------ */

    private void processSingle(Trade trade) {
        log.debug("Processing trade #{} on thread {}", trade.getTradeId(),
                  Thread.currentThread().getName());

        // 1. Structural validation
        TradeValidator.ValidationResult validation = TradeValidator.validate(trade);
        if (!validation.isValid()) {
            trade.reject("Validation failed: " + validation.getSummary());
            persistAndCount(trade);
            return;
        }

        // 2. Portfolio check for SELL orders (prevents negative quantity)
        if (trade.getSide() == Trade.Side.SELL) {
            boolean sufficient = portfolioManager.applySell(trade);
            if (!sufficient) {
                trade.reject(String.format(
                        "Insufficient position: account=%d symbol=%s requested=%d",
                        trade.getAccountId(), trade.getSymbol(), trade.getQuantity()));
                persistAndCount(trade);
                return;
            }
            trade.accept();
        } else {
            // BUY — always valid once structural validation passes
            portfolioManager.applyBuy(trade);
            trade.accept();
        }

        // 3. Sync position back to DB (best-effort — non-fatal if this fails)
        syncPositionToDB(trade);

        persistAndCount(trade);
    }

    private void persistAndCount(Trade trade) {
        try {
            dbManager.persistTrade(trade);
            if (trade.getStatus() == Trade.Status.ACCEPTED) {
                acceptedCount.increment();
            } else {
                rejectedCount.increment();
            }
        } catch (SQLException e) {
            log.error("DB persistence failed for trade #{}: {}", trade.getTradeId(), e.getMessage());
            errorCount.increment();
        }
    }

    private void syncPositionToDB(Trade trade) {
        try {
            var pos = portfolioManager.getPosition(trade.getAccountId(), trade.getSymbol());
            if (pos != null) {
                dbManager.upsertPosition(
                        pos.getAccountId(), pos.getSymbol(),
                        pos.getNetQuantity(), pos.getAverageCost(), pos.getRealizedPnL());
            }
        } catch (SQLException e) {
            log.warn("Position sync to DB failed for ({}, {}): {}",
                     trade.getAccountId(), trade.getSymbol(), e.getMessage());
        }
    }

    /** Ensure all unique account IDs are present in the DB before FK constraints fire. */
    private void ensureAccounts(List<Trade> trades) {
        trades.stream()
              .map(Trade::getAccountId)
              .distinct()
              .forEach(accountId -> {
                  try {
                      dbManager.upsertAccount(accountId, "Account-" + accountId);
                  } catch (SQLException e) {
                      log.error("Failed to upsert account {}: {}", accountId, e.getMessage());
                  }
              });
    }

    /* ------------------------------------------------------------------ */
    /*  Result record                                                        */
    /* ------------------------------------------------------------------ */

    public record ProcessingResult(
            int  totalTrades,
            int  completedTrades,
            long accepted,
            long rejected,
            long errors,
            long elapsedMs
    ) {}

    /* ------------------------------------------------------------------ */
    /*  Named thread factory for better diagnostics                         */
    /* ------------------------------------------------------------------ */

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String     prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        NamedThreadFactory(String prefix) { this.prefix = prefix; }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
