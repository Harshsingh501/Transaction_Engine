package com.tradingdesk.validation;

import com.tradingdesk.model.Trade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless validator for Trade objects.
 * Returns a {@link ValidationResult} containing all violations found.
 * Multiple rules are evaluated per trade so every failure is reported together.
 */
public final class TradeValidator {

    private TradeValidator() { /* utility class */ }

    public static ValidationResult validate(Trade trade) {
        List<String> violations = new ArrayList<>();

        // Rule 1: Quantity must be strictly positive (no negative or zero)
        if (trade.getQuantity() <= 0) {
            violations.add(String.format(
                    "Quantity must be positive (got %d)", trade.getQuantity()));
        }

        // Rule 2: Price must be positive
        if (trade.getPrice() == null || trade.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            violations.add(String.format(
                    "Price must be positive (got %s)", trade.getPrice()));
        }

        // Rule 3: Symbol must be non-blank
        if (trade.getSymbol() == null || trade.getSymbol().isBlank()) {
            violations.add("Symbol must not be blank");
        }

        // Rule 4: Side must be present
        if (trade.getSide() == null) {
            violations.add("Trade side (BUY/SELL) must be specified");
        }

        // Rule 5: AccountId must be positive
        if (trade.getAccountId() <= 0) {
            violations.add(String.format(
                    "AccountId must be positive (got %d)", trade.getAccountId()));
        }

        // Rule 6: Timestamp must be present
        if (trade.getTimestamp() == null) {
            violations.add("Timestamp must not be null");
        }

        return violations.isEmpty()
               ? ValidationResult.ok()
               : ValidationResult.fail(violations);
    }

    /* ------------------------------------------------------------------ */
    /*  Result type                                                          */
    /* ------------------------------------------------------------------ */

    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> violations;

        private ValidationResult(boolean valid, List<String> violations) {
            this.valid      = valid;
            this.violations = List.copyOf(violations);
        }

        public static ValidationResult ok()                          { return new ValidationResult(true,  List.of()); }
        public static ValidationResult fail(List<String> violations) { return new ValidationResult(false, violations); }

        public boolean      isValid()     { return valid; }
        public List<String> getViolations(){ return violations; }
        public String       getSummary()  { return String.join("; ", violations); }
    }
}
