package com.tradingdesk.loader;

import com.tradingdesk.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads trade requests from a CSV file.
 * Format: tradeId,accountId,symbol,quantity,price,side,timestamp
 *
 * <p>Validation performed at parse time:
 * <ul>
 *   <li>Quantity must be positive (> 0)</li>
 *   <li>Price must be positive</li>
 *   <li>Side must be BUY or SELL</li>
 *   <li>Timestamp must be parseable ISO-8601</li>
 * </ul>
 */
public class TradeFileLoader {

    private static final Logger log = LoggerFactory.getLogger(TradeFileLoader.class);

    private static final int COL_TRADE_ID   = 0;
    private static final int COL_ACCOUNT_ID = 1;
    private static final int COL_SYMBOL     = 2;
    private static final int COL_QUANTITY   = 3;
    private static final int COL_PRICE      = 4;
    private static final int COL_SIDE       = 5;
    private static final int COL_TIMESTAMP  = 6;

    private final Path filePath;

    public TradeFileLoader(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Reads and parses the CSV file, returning successfully parsed Trade objects.
     * Malformed lines are logged and skipped.
     */
    public List<Trade> load() throws IOException {
        log.info("Loading trades from: {}", filePath.toAbsolutePath());

        List<Trade>   trades      = new ArrayList<>();
        AtomicInteger lineNumber  = new AtomicInteger(0);
        AtomicInteger skipped     = new AtomicInteger(0);

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int lineNum = lineNumber.incrementAndGet();

                // Skip header line
                if (lineNum == 1) continue;

                // Skip blank lines
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length < 7) {
                    log.warn("Line {}: Insufficient columns ({}) – skipping: [{}]",
                             lineNum, parts.length, line);
                    skipped.incrementAndGet();
                    continue;
                }

                try {
                    Trade trade = parseTrade(parts, lineNum);
                    trades.add(trade);
                } catch (IllegalArgumentException e) {
                    log.warn("Line {}: Parse error – {} – skipping: [{}]",
                             lineNum, e.getMessage(), line);
                    skipped.incrementAndGet();
                }
            }
        }

        log.info("Loaded {} trades ({} lines skipped/invalid)", trades.size(), skipped.get());
        return Collections.unmodifiableList(trades);
    }

    /* ------------------------------------------------------------------ */
    /*  Private helpers                                                      */
    /* ------------------------------------------------------------------ */

    private Trade parseTrade(String[] parts, int lineNum) {
        long tradeId = parseLong("tradeId", parts[COL_TRADE_ID].trim(), lineNum);
        long accountId = parseLong("accountId", parts[COL_ACCOUNT_ID].trim(), lineNum);
        String symbol = parts[COL_SYMBOL].trim().toUpperCase();

        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol is empty");
        }

        int quantity = parseInt("quantity", parts[COL_QUANTITY].trim(), lineNum);
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive (got " + quantity + ")");
        }

        BigDecimal price = parseDecimal("price", parts[COL_PRICE].trim(), lineNum);
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Price must be positive (got " + price + ")");
        }

        Trade.Side side = parseSide(parts[COL_SIDE].trim().toUpperCase(), lineNum);
        LocalDateTime timestamp = parseTimestamp(parts[COL_TIMESTAMP].trim(), lineNum);

        return new Trade.Builder()
                .tradeId(tradeId)
                .accountId(accountId)
                .symbol(symbol)
                .quantity(quantity)
                .price(price)
                .side(side)
                .timestamp(timestamp)
                .build();
    }

    private long parseLong(String field, String value, int line) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " '" + value + "' at line " + line);
        }
    }

    private int parseInt(String field, String value, int line) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " '" + value + "' at line " + line);
        }
    }

    private BigDecimal parseDecimal(String field, String value, int line) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " '" + value + "' at line " + line);
        }
    }

    private Trade.Side parseSide(String value, int line) {
        try {
            return Trade.Side.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid side '" + value + "' at line " + line + ". Must be BUY or SELL");
        }
    }

    private LocalDateTime parseTimestamp(String value, int line) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid timestamp '" + value + "' at line " + line + ". Expected ISO-8601 format");
        }
    }
}
