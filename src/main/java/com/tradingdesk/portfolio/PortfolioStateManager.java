package com.tradingdesk.portfolio;

import com.tradingdesk.model.Position;
import com.tradingdesk.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory portfolio state store.
 *
 * <p>Key: "{accountId}:{symbol}"
 *
 * <p>Uses {@link ConcurrentHashMap} for lock-free structural modifications and
 * delegates position-level thread safety to {@link Position}'s own synchronized methods.
 */
public class PortfolioStateManager {

    private static final Logger log = LoggerFactory.getLogger(PortfolioStateManager.class);

    /** Composite key: accountId + ":" + symbol */
    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    /* ------------------------------------------------------------------ */
    /*  Public API                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Applies a validated BUY trade to the portfolio.
     * Creates a new Position if one does not yet exist for (account, symbol).
     */
    public void applyBuy(Trade trade) {
        Position pos = getOrCreatePosition(trade.getAccountId(), trade.getSymbol());
        pos.applyBuy(trade.getQuantity(), trade.getPrice());
        log.debug("BUY applied  → {}", pos);
    }

    /**
     * Attempts to apply a SELL trade to the portfolio.
     *
     * @return {@code true}  if sufficient quantity exists and the sell was applied
     *         {@code false} if the position has insufficient quantity
     */
    public boolean applySell(Trade trade) {
        Position pos = getOrCreatePosition(trade.getAccountId(), trade.getSymbol());
        boolean success = pos.applySell(trade.getQuantity(), trade.getPrice());
        if (success) {
            log.debug("SELL applied → {}", pos);
        } else {
            log.warn("SELL REJECTED (insufficient qty) → account={} symbol={} wanted={} have={}",
                     trade.getAccountId(), trade.getSymbol(),
                     trade.getQuantity(), pos.getNetQuantity());
        }
        return success;
    }

    /**
     * Retrieves a position snapshot. Returns {@code null} if the position has never been opened.
     */
    public Position getPosition(long accountId, String symbol) {
        return positions.get(positionKey(accountId, symbol));
    }

    /** Returns all positions currently held in memory. */
    public Collection<Position> getAllPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    /**
     * Returns all positions grouped by accountId – useful for per-account reporting.
     */
    public Map<Long, List<Position>> getPositionsByAccount() {
        return positions.values().stream()
                        .collect(Collectors.groupingBy(Position::getAccountId));
    }

    /* ------------------------------------------------------------------ */
    /*  Private helpers                                                      */
    /* ------------------------------------------------------------------ */

    private Position getOrCreatePosition(long accountId, String symbol) {
        String key = positionKey(accountId, symbol);
        // computeIfAbsent is atomic in ConcurrentHashMap
        return positions.computeIfAbsent(key, k -> new Position(accountId, symbol));
    }

    private static String positionKey(long accountId, String symbol) {
        return accountId + ":" + symbol;
    }
}
