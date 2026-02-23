package com.tradingdesk.model;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory position for a single (accountId, symbol) pair.
 * Uses atomic operations to ensure lock-free updates where possible.
 */
public final class Position {

    private final long   accountId;
    private final String symbol;

    /* Atomic integer for net quantity to ensure thread-safe updates */
    private final AtomicInteger         netQuantity     = new AtomicInteger(0);
    private final AtomicReference<BigDecimal> averageCost = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> realizedPnL = new AtomicReference<>(BigDecimal.ZERO);

    /* Track total BUY and SELL separately for reporting */
    private final AtomicInteger totalBought = new AtomicInteger(0);
    private final AtomicInteger totalSold   = new AtomicInteger(0);

    public Position(long accountId, String symbol) {
        this.accountId = accountId;
        this.symbol    = symbol;
    }

    /**
     * Apply a BUY trade: increases quantity, recalculates weighted-average cost.
     */
    public synchronized void applyBuy(int qty, BigDecimal price) {
        int    currentQty  = netQuantity.get();
        BigDecimal currentAvg = averageCost.get();

        BigDecimal totalCost = currentAvg.multiply(BigDecimal.valueOf(currentQty))
                                         .add(price.multiply(BigDecimal.valueOf(qty)));

        int newQty = currentQty + qty;
        netQuantity.set(newQty);
        averageCost.set(newQty == 0 ? BigDecimal.ZERO
                                    : totalCost.divide(BigDecimal.valueOf(newQty), 6, java.math.RoundingMode.HALF_UP));
        totalBought.addAndGet(qty);
    }

    /**
     * Apply a SELL trade: decreases quantity, realizes PnL.
     * Returns false if insufficient quantity (would cause negative position).
     */
    public synchronized boolean applySell(int qty, BigDecimal price) {
        int currentQty = netQuantity.get();
        if (currentQty < qty) {
            return false; // Insufficient quantity – trade will be rejected
        }
        BigDecimal avgCost   = averageCost.get();
        BigDecimal pnl       = price.subtract(avgCost).multiply(BigDecimal.valueOf(qty));
        realizedPnL.updateAndGet(current -> current.add(pnl));
        netQuantity.addAndGet(-qty);
        totalSold.addAndGet(qty);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  Getters (snapshot values)                                            */
    /* ------------------------------------------------------------------ */
    public long       getAccountId()  { return accountId; }
    public String     getSymbol()     { return symbol; }
    public int        getNetQuantity(){ return netQuantity.get(); }
    public BigDecimal getAverageCost(){ return averageCost.get(); }
    public BigDecimal getRealizedPnL(){ return realizedPnL.get(); }
    public int        getTotalBought(){ return totalBought.get(); }
    public int        getTotalSold()  { return totalSold.get(); }

    @Override
    public String toString() {
        return String.format("Position{acct=%d, sym=%s, qty=%d, avgCost=%.4f, realizedPnL=%.2f}",
                accountId, symbol, netQuantity.get(), averageCost.get(), realizedPnL.get());
    }
}
