package com.tradingdesk.db;

import com.tradingdesk.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Manages database schema creation and all JDBC interactions.
 * Uses H2 embedded database for zero-configuration relational persistence.
 *
 * Schema:
 *   accounts  (1) → trades (N)
 *   accounts  (1) → positions (N)
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    // H2 in-process DB; file-backed so data survives JVM restart
    private static final String JDBC_URL  = "jdbc:h2:./trading_engine_db;MODE=MySQL;AUTO_SERVER=TRUE";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASS = "";

    private final Connection connection;

    public DatabaseManager() throws SQLException {
        // Register H2 driver (class-path loaded)
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 driver not found on classpath", e);
        }

        connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
        connection.setAutoCommit(false);   // explicit transaction control
        log.info("Connected to H2 database at {}", JDBC_URL);
        initSchema();
    }

    /* ------------------------------------------------------------------ */
    /*  Schema Initialization                                                */
    /* ------------------------------------------------------------------ */

    private void initSchema() throws SQLException {
        log.info("Initializing database schema …");
        try (Statement st = connection.createStatement()) {

            // Drop tables to support clean re-runs (foreign key order matters)
            st.execute("DROP TABLE IF EXISTS positions");
            st.execute("DROP TABLE IF EXISTS trades");
            st.execute("DROP TABLE IF EXISTS accounts");

            // ── ACCOUNTS TABLE ──────────────────────────────────────────
            st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        account_id   BIGINT PRIMARY KEY,
                        account_name VARCHAR(64) NOT NULL,
                        created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            // ── TRADES TABLE ─────────────────────────────────────────────
            st.execute("""
                    CREATE TABLE IF NOT EXISTS trades (
                        trade_id         BIGINT PRIMARY KEY,
                        account_id       BIGINT     NOT NULL,
                        symbol           VARCHAR(20) NOT NULL,
                        quantity         INT        NOT NULL CHECK (quantity > 0),
                        price            DECIMAL(18,6) NOT NULL CHECK (price > 0),
                        side             VARCHAR(4) NOT NULL,
                        status           VARCHAR(10) NOT NULL,
                        rejection_reason VARCHAR(256),
                        trade_timestamp  TIMESTAMP  NOT NULL,
                        processed_at     TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_trade_account FOREIGN KEY (account_id)
                            REFERENCES accounts(account_id) ON DELETE CASCADE
                    )
                    """);

            // ── POSITIONS TABLE ───────────────────────────────────────────
            st.execute("""
                    CREATE TABLE IF NOT EXISTS positions (
                        account_id   BIGINT     NOT NULL,
                        symbol       VARCHAR(20) NOT NULL,
                        net_quantity INT        NOT NULL DEFAULT 0,
                        average_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
                        realized_pnl DECIMAL(18,6) NOT NULL DEFAULT 0,
                        updated_at   TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (account_id, symbol),
                        CONSTRAINT fk_pos_account FOREIGN KEY (account_id)
                            REFERENCES accounts(account_id) ON DELETE CASCADE
                    )
                    """);

            connection.commit();
            log.info("Schema ready (tables: accounts, trades, positions)");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Account operations                                                   */
    /* ------------------------------------------------------------------ */

    private static final String UPSERT_ACCOUNT = """
            MERGE INTO accounts (account_id, account_name)
            KEY(account_id)
            VALUES (?, ?)
            """;

    public synchronized void upsertAccount(long accountId, String accountName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_ACCOUNT)) {
            ps.setLong(1, accountId);
            ps.setString(2, accountName);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Trade persistence                                                    */
    /* ------------------------------------------------------------------ */

    private static final String INSERT_TRADE = """
            INSERT INTO trades
              (trade_id, account_id, symbol, quantity, price, side,
               status, rejection_reason, trade_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_TRADE_STATUS = """
            UPDATE trades
               SET status = ?, rejection_reason = ?, processed_at = CURRENT_TIMESTAMP
             WHERE trade_id = ?
            """;

    /**
     * Inserts a trade record. Called after validation. Thread-safe via synchronized.
     */
    public synchronized void persistTrade(Trade trade) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_TRADE)) {
            ps.setLong(1, trade.getTradeId());
            ps.setLong(2, trade.getAccountId());
            ps.setString(3, trade.getSymbol());
            ps.setInt(4, trade.getQuantity());
            ps.setBigDecimal(5, trade.getPrice());
            ps.setString(6, trade.getSide().name());
            ps.setString(7, trade.getStatus().name());
            ps.setString(8, trade.getRejectionReason());
            ps.setTimestamp(9, Timestamp.valueOf(trade.getTimestamp()));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /**
     * Updates status of an already-inserted trade row.
     */
    public synchronized void updateTradeStatus(Trade trade) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_TRADE_STATUS)) {
            ps.setString(1, trade.getStatus().name());
            ps.setString(2, trade.getRejectionReason());
            ps.setLong(3, trade.getTradeId());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Position persistence                                                 */
    /* ------------------------------------------------------------------ */

    private static final String UPSERT_POSITION = """
            MERGE INTO positions (account_id, symbol, net_quantity, average_cost, realized_pnl, updated_at)
            KEY(account_id, symbol)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    public synchronized void upsertPosition(long accountId, String symbol,
                                            int netQty, java.math.BigDecimal avgCost,
                                            java.math.BigDecimal realizedPnL) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_POSITION)) {
            ps.setLong(1, accountId);
            ps.setString(2, symbol);
            ps.setInt(3, netQty);
            ps.setBigDecimal(4, avgCost);
            ps.setBigDecimal(5, realizedPnL);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Reporting queries                                                    */
    /* ------------------------------------------------------------------ */

    public ResultSet queryAllTrades() throws SQLException {
        Statement st = connection.createStatement();
        return st.executeQuery(
                "SELECT * FROM trades ORDER BY account_id, trade_timestamp");
    }

    public ResultSet queryAllPositions() throws SQLException {
        Statement st = connection.createStatement();
        return st.executeQuery(
                "SELECT * FROM positions ORDER BY account_id, symbol");
    }

    public ResultSet queryTradeSummaryByAccount() throws SQLException {
        Statement st = connection.createStatement();
        return st.executeQuery("""
                SELECT account_id,
                       COUNT(*) AS total_trades,
                       SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted,
                       SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected,
                       SUM(CASE WHEN status = 'ACCEPTED' AND side = 'BUY'  THEN quantity * price ELSE 0 END) AS buy_notional,
                       SUM(CASE WHEN status = 'ACCEPTED' AND side = 'SELL' THEN quantity * price ELSE 0 END) AS sell_notional
                  FROM trades
                 GROUP BY account_id
                 ORDER BY account_id
                """);
    }

    /* ------------------------------------------------------------------ */
    /*  Lifecycle                                                            */
    /* ------------------------------------------------------------------ */

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("Database connection closed.");
            }
        } catch (SQLException e) {
            log.error("Error closing database connection: {}", e.getMessage());
        }
    }
}
