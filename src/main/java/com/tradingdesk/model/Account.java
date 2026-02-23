package com.tradingdesk.model;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a trading account that aggregates multiple Trades and Positions.
 * One Account → multiple Trades (1:N)
 * One Account → multiple Positions (1:N)
 * Thread-safe via CopyOnWriteArrayList for trade history.
 */
public final class Account {

    private final long accountId;
    private final String accountName;

    /* Trade history list – safe for concurrent reads, infrequent writes */
    private final CopyOnWriteArrayList<Trade> tradeHistory = new CopyOnWriteArrayList<>();

    public Account(long accountId, String accountName) {
        this.accountId   = accountId;
        this.accountName = accountName;
    }

    public long   getAccountId()   { return accountId; }
    public String getAccountName() { return accountName; }

    public void addTrade(Trade trade) {
        tradeHistory.add(trade);
    }

    /** Returns an unmodifiable snapshot of the trade history. */
    public List<Trade> getTradeHistory() {
        return Collections.unmodifiableList(tradeHistory);
    }

    public long getAcceptedTradeCount() {
        return tradeHistory.stream()
                           .filter(t -> t.getStatus() == Trade.Status.ACCEPTED)
                           .count();
    }

    public long getRejectedTradeCount() {
        return tradeHistory.stream()
                           .filter(t -> t.getStatus() == Trade.Status.REJECTED)
                           .count();
    }

    @Override
    public String toString() {
        return String.format("Account{id=%d, name=%s, trades=%d}",
                accountId, accountName, tradeHistory.size());
    }
}
