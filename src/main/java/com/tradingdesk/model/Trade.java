package com.tradingdesk.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable Trade model representing a single trade request.
 * Enforces business constraints: quantity must be positive.
 */
public final class Trade {

    public enum Side { BUY, SELL }

    public enum Status { PENDING, ACCEPTED, REJECTED }

    private final long tradeId;
    private final long accountId;
    private final String symbol;
    private final int quantity;
    private final BigDecimal price;
    private final Side side;
    private final LocalDateTime timestamp;
    private volatile Status status;
    private volatile String rejectionReason;

    private Trade(Builder builder) {
        this.tradeId        = builder.tradeId;
        this.accountId      = builder.accountId;
        this.symbol         = Objects.requireNonNull(builder.symbol, "Symbol must not be null");
        this.quantity       = builder.quantity;
        this.price          = Objects.requireNonNull(builder.price, "Price must not be null");
        this.side           = Objects.requireNonNull(builder.side, "Side must not be null");
        this.timestamp      = Objects.requireNonNull(builder.timestamp, "Timestamp must not be null");
        this.status         = Status.PENDING;
        this.rejectionReason = null;
    }

    /* ------------------------------------------------------------------ */
    /*  Getters                                                              */
    /* ------------------------------------------------------------------ */
    public long        getTradeId()         { return tradeId; }
    public long        getAccountId()       { return accountId; }
    public String      getSymbol()          { return symbol; }
    public int         getQuantity()        { return quantity; }
    public BigDecimal  getPrice()           { return price; }
    public Side        getSide()            { return side; }
    public LocalDateTime getTimestamp()     { return timestamp; }
    public Status      getStatus()          { return status; }
    public String      getRejectionReason() { return rejectionReason; }

    /* ------------------------------------------------------------------ */
    /*  State Transitions                                                    */
    /* ------------------------------------------------------------------ */
    public void accept() {
        this.status = Status.ACCEPTED;
    }

    public void reject(String reason) {
        this.status          = Status.REJECTED;
        this.rejectionReason = reason;
    }

    /** Computed notional value of this trade. */
    public BigDecimal getNotionalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /* ------------------------------------------------------------------ */
    /*  Object overrides                                                     */
    /* ------------------------------------------------------------------ */
    @Override
    public String toString() {
        return String.format("Trade{id=%d, acct=%d, %s %d %s @ %.2f [%s]}",
                tradeId, accountId, side, quantity, symbol, price, status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade t)) return false;
        return tradeId == t.tradeId;
    }

    @Override
    public int hashCode() { return Long.hashCode(tradeId); }

    /* ------------------------------------------------------------------ */
    /*  Builder                                                              */
    /* ------------------------------------------------------------------ */
    public static final class Builder {
        private long tradeId;
        private long accountId;
        private String symbol;
        private int quantity;
        private BigDecimal price;
        private Side side;
        private LocalDateTime timestamp;

        public Builder tradeId(long tradeId)             { this.tradeId   = tradeId;   return this; }
        public Builder accountId(long accountId)         { this.accountId = accountId; return this; }
        public Builder symbol(String symbol)             { this.symbol    = symbol;    return this; }
        public Builder quantity(int quantity)            { this.quantity  = quantity;  return this; }
        public Builder price(BigDecimal price)           { this.price     = price;     return this; }
        public Builder side(Side side)                   { this.side      = side;      return this; }
        public Builder timestamp(LocalDateTime timestamp){ this.timestamp  = timestamp; return this; }

        public Trade build() { return new Trade(this); }
    }
}
