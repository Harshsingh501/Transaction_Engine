package com.tradingdesk.engine;

import com.tradingdesk.db.DatabaseManager;
import com.tradingdesk.loader.TradeFileLoader;
import com.tradingdesk.model.Trade;
import com.tradingdesk.portfolio.PortfolioStateManager;
import com.tradingdesk.processor.TradeProcessor;
import com.tradingdesk.report.ReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║   High-Throughput Transaction Processing Engine — Entry Point        ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║  Pipeline:                                                           ║
 * ║   1. Load trade requests from CSV file                               ║
 * ║   2. Validate & process trades concurrently (ExecutorService)        ║
 * ║   3. Persist trades into H2 relational database                      ║
 * ║   4. Maintain in-memory portfolio state (ConcurrentHashMap)          ║
 * ║   5. Generate summary reports via Java Streams                       ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
public class TransactionEngineMain {

    private static final Logger log = LoggerFactory.getLogger(TransactionEngineMain.class);

    public static void main(String[] args) {
        // Resolve the trade file path
        String csvPath = args.length > 0 ? args[0] : "trades.csv";
        Path tradePath = Paths.get(csvPath).toAbsolutePath();

        printBanner();
        log.info("Starting Transaction Processing Engine");
        log.info("Trade file: {}", tradePath);

        DatabaseManager      dbManager  = null;
        TradeProcessor       processor  = null;

        try {
            // ── Step 1: Initialize components ───────────────────────────
            dbManager  = new DatabaseManager();
            PortfolioStateManager portfolio = new PortfolioStateManager();
            processor  = new TradeProcessor(dbManager, portfolio);

            // ── Step 2: Load trade requests from file ────────────────────
            TradeFileLoader loader = new TradeFileLoader(tradePath);
            List<Trade>     trades = loader.load();

            if (trades.isEmpty()) {
                log.warn("No trades loaded from file. Exiting.");
                return;
            }

            // ── Step 3: Process trades concurrently ──────────────────────
            TradeProcessor.ProcessingResult result = processor.processAll(trades);

            // ── Step 4: Generate reports ─────────────────────────────────
            ReportGenerator reporter = new ReportGenerator();
            reporter.generateAllReports(trades, portfolio, result);

            log.info("Engine completed successfully.");

        } catch (IOException e) {
            log.error("Failed to read trade file '{}': {}", tradePath, e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            log.error("Database error: {}", e.getMessage());
            System.exit(2);
        } catch (InterruptedException e) {
            log.error("Processing interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
            System.exit(3);
        } finally {
            // ── Cleanup ───────────────────────────────────────────────────
            if (processor  != null) processor.shutdown();
            if (dbManager  != null) dbManager.close();
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("+======================================================================+");
        System.out.println("|         HIGH-THROUGHPUT TRANSACTION PROCESSING ENGINE                |");
        System.out.println("|                    Trading Desk System  v1.0.0                      |");
        System.out.println("+======================================================================+");
        System.out.println("|  >> Concurrent Trade Processing   (ExecutorService + thread pool)   |");
        System.out.println("|  >> Relational Persistence        (H2 Database via JDBC)            |");
        System.out.println("|  >> In-Memory Portfolio State     (ConcurrentHashMap)               |");
        System.out.println("|  >> Stream-Based Report Generation (Java Streams API)              |");
        System.out.println("|  >> Thread-Safe Data Integrity    (synchronized + AtomicInteger)    |");
        System.out.println("+======================================================================+");
        System.out.println();
    }
}
