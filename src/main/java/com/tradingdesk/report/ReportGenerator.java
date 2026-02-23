package com.tradingdesk.report;

import com.tradingdesk.model.Position;
import com.tradingdesk.model.Trade;
import com.tradingdesk.portfolio.PortfolioStateManager;
import com.tradingdesk.processor.TradeProcessor.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates formatted summary reports using the Java Streams API.
 *
 * Reports included:
 *  1. Processing Summary     – high-level counts and throughput
 *  2. Trade Status Report    – per-account accepted/rejected breakdown
 *  3. Portfolio Report       – per-account, per-symbol position snapshot
 *  4. Symbol Activity Report – cross-account volume and notional per symbol
 *  5. Top Accounts Report    – accounts ranked by total notional value
 *  6. Rejected Trades Log    – all rejected trades with rejection reasons
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);
    private static final String LINE = "=".repeat(80);
    private static final String DIV  = "-".repeat(80);

    /* ------------------------------------------------------------------ */
    /*  Master Report Entry Point                                           */
    /* ------------------------------------------------------------------ */

    public void generateAllReports(List<Trade> allTrades,
                                   PortfolioStateManager portfolio,
                                   ProcessingResult result) {
        log.info("Generating summary reports …");
        System.out.println();
        printProcessingSummary(result);
        printTradeStatusReport(allTrades);
        printPortfolioReport(portfolio);
        printSymbolActivityReport(allTrades);
        printTopAccountsReport(allTrades);
        printRejectedTradesLog(allTrades);
        log.info("All reports generated.");
    }

    /* ------------------------------------------------------------------ */
    /*  1. Processing Summary                                               */
    /* ------------------------------------------------------------------ */

    private void printProcessingSummary(ProcessingResult result) {
        System.out.println(LINE);
        System.out.println("  [SUMMARY]  PROCESSING SUMMARY REPORT");
        System.out.println(LINE);
        System.out.printf("  %-30s %d%n",   "Total Trades Submitted:",   result.totalTrades());
        System.out.printf("  %-30s %d%n",   "Completed:",                result.completedTrades());
        System.out.printf("  %-30s %d%n",   "Accepted:",                 result.accepted());
        System.out.printf("  %-30s %d%n",   "Rejected:",                 result.rejected());
        System.out.printf("  %-30s %d%n",   "Errors:",                   result.errors());
        System.out.printf("  %-30s %d ms%n","Elapsed Time:",             result.elapsedMs());
        if (result.elapsedMs() > 0) {
            double tps = (result.completedTrades() * 1000.0) / result.elapsedMs();
            System.out.printf("  %-30s %.2f trades/sec%n", "Throughput:", tps);
        }
        double successRate = result.totalTrades() == 0 ? 0
                : (result.accepted() * 100.0) / result.totalTrades();
        System.out.printf("  %-30s %.1f%%%n", "Acceptance Rate:", successRate);
        System.out.println(LINE);
        System.out.println();
    }

    /* ------------------------------------------------------------------ */
    /*  2. Trade Status Report                                              */
    /* ------------------------------------------------------------------ */

    private void printTradeStatusReport(List<Trade> trades) {
        System.out.println(LINE);
        System.out.println("  [STATUS]   TRADE STATUS REPORT  (per Account)");
        System.out.println(LINE);
        System.out.printf("  %-12s %-10s %-10s %-10s %-20s %-20s%n",
                "Account", "Total", "Accepted", "Rejected", "Buy Notional", "Sell Notional");
        System.out.println(DIV);

        // Group trades by accountId using Streams
        trades.stream()
              .collect(Collectors.groupingBy(Trade::getAccountId,
                                             TreeMap::new,
                                             Collectors.toList()))
              .forEach((accountId, accountTrades) -> {
                  long accepted      = accountTrades.stream().filter(t -> t.getStatus() == Trade.Status.ACCEPTED).count();
                  long rejected      = accountTrades.stream().filter(t -> t.getStatus() == Trade.Status.REJECTED).count();
                  BigDecimal buyNot  = accountTrades.stream()
                          .filter(t -> t.getStatus() == Trade.Status.ACCEPTED && t.getSide() == Trade.Side.BUY)
                          .map(Trade::getNotionalValue)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
                  BigDecimal sellNot = accountTrades.stream()
                          .filter(t -> t.getStatus() == Trade.Status.ACCEPTED && t.getSide() == Trade.Side.SELL)
                          .map(Trade::getNotionalValue)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);

                  System.out.printf("  %-12d %-10d %-10d %-10d %-20.2f %-20.2f%n",
                          accountId, accountTrades.size(), accepted, rejected, buyNot, sellNot);
              });

        System.out.println(LINE);
        System.out.println();
    }

    /* ------------------------------------------------------------------ */
    /*  3. Portfolio Report                                                 */
    /* ------------------------------------------------------------------ */

    private void printPortfolioReport(PortfolioStateManager portfolio) {
        System.out.println(LINE);
        System.out.println("  [PORTFOLIO] PORTFOLIO POSITIONS REPORT  (In-Memory State)");
        System.out.println(LINE);
        System.out.printf("  %-12s %-12s %-10s %-14s %-16s%n",
                "Account", "Symbol", "Net Qty", "Avg Cost", "Realized PnL");
        System.out.println(DIV);

        portfolio.getPositionsByAccount()
                 .entrySet().stream()
                 .sorted(Map.Entry.comparingByKey())
                 .forEach(entry -> {
                     long        accountId = entry.getKey();
                     List<Position> positions = entry.getValue().stream()
                             .sorted(Comparator.comparing(Position::getSymbol))
                             .toList();

                     positions.forEach(pos -> {
                         String pnlSign = pos.getRealizedPnL().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                         System.out.printf("  %-12d %-12s %-10d %-14.4f %s%-16.2f%n",
                                 accountId, pos.getSymbol(),
                                 pos.getNetQuantity(),
                                 pos.getAverageCost(),
                                 pnlSign, pos.getRealizedPnL());
                     });

                     // Per-account total realized PnL
                     BigDecimal totalPnL = positions.stream()
                             .map(Position::getRealizedPnL)
                             .reduce(BigDecimal.ZERO, BigDecimal::add);
                     System.out.printf("  %-12s %-12s %-10s %-14s Total: %.2f%n",
                             "", "", "", "", totalPnL);
                     System.out.println(DIV);
                 });

        System.out.println(LINE);
        System.out.println();
    }

    /* ------------------------------------------------------------------ */
    /*  4. Symbol Activity Report                                           */
    /* ------------------------------------------------------------------ */

    private void printSymbolActivityReport(List<Trade> trades) {
        System.out.println(LINE);
        System.out.println("  [SYMBOLS]  SYMBOL ACTIVITY REPORT  (Cross-Account)");
        System.out.println(LINE);
        System.out.printf("  %-14s %-12s %-12s %-14s %-14s %-16s%n",
                "Symbol", "Buy Trades", "Sell Trades", "Buy Volume", "Sell Volume", "Net Notional");
        System.out.println(DIV);

        // Group accepted trades by symbol, aggregate via Streams
        record SymbolStats(long buyTrades, long sellTrades,
                           int buyVol, int sellVol,
                           BigDecimal netNotional) {}

        trades.stream()
              .filter(t -> t.getStatus() == Trade.Status.ACCEPTED)
              .collect(Collectors.groupingBy(Trade::getSymbol))
              .entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(e -> {
                  String      symbol      = e.getKey();
                  List<Trade> symbolTrades = e.getValue();

                  long buyTrades  = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.BUY).count();
                  long sellTrades = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.SELL).count();
                  int  buyVol     = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.BUY)
                                               .mapToInt(Trade::getQuantity).sum();
                  int  sellVol    = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.SELL)
                                               .mapToInt(Trade::getQuantity).sum();
                  BigDecimal buyNot  = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.BUY)
                                                   .map(Trade::getNotionalValue)
                                                   .reduce(BigDecimal.ZERO, BigDecimal::add);
                  BigDecimal sellNot = symbolTrades.stream().filter(t -> t.getSide() == Trade.Side.SELL)
                                                   .map(Trade::getNotionalValue)
                                                   .reduce(BigDecimal.ZERO, BigDecimal::add);
                  BigDecimal netNot  = buyNot.subtract(sellNot);

                  System.out.printf("  %-14s %-12d %-12d %-14d %-14d %-16.2f%n",
                          symbol, buyTrades, sellTrades, buyVol, sellVol, netNot);
              });

        System.out.println(LINE);
        System.out.println();
    }

    /* ------------------------------------------------------------------ */
    /*  5. Top Accounts by Notional Value                                   */
    /* ------------------------------------------------------------------ */

    private void printTopAccountsReport(List<Trade> trades) {
        System.out.println(LINE);
        System.out.println("  [TOP-10]   TOP ACCOUNTS BY TOTAL NOTIONAL VALUE");
        System.out.println(LINE);
        System.out.printf("  %-6s %-12s %-22s%n", "Rank", "Account", "Total Notional Value");
        System.out.println(DIV);

        int[] rank = {1};
        trades.stream()
              .filter(t -> t.getStatus() == Trade.Status.ACCEPTED)
              .collect(Collectors.groupingBy(
                      Trade::getAccountId,
                      Collectors.reducing(BigDecimal.ZERO,
                                          Trade::getNotionalValue,
                                          BigDecimal::add)))
              .entrySet().stream()
              .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
              .limit(10)
              .forEach(e ->
                  System.out.printf("  %-6d %-12d %-22.2f%n",
                          rank[0]++, e.getKey(), e.getValue())
              );

        System.out.println(LINE);
        System.out.println();
    }

    /* ------------------------------------------------------------------ */
    /*  6. Rejected Trades Log                                              */
    /* ------------------------------------------------------------------ */

    private void printRejectedTradesLog(List<Trade> trades) {
        List<Trade> rejected = trades.stream()
                .filter(t -> t.getStatus() == Trade.Status.REJECTED)
                .sorted(Comparator.comparingLong(Trade::getTradeId))
                .toList();

        System.out.println(LINE);
        System.out.printf("  [REJECTED] REJECTED TRADES LOG  (%d rejected)%n", rejected.size());
        System.out.println(LINE);

        if (rejected.isEmpty()) {
            System.out.println("  All trades were accepted successfully.");
        } else {
            System.out.printf("  %-10s %-10s %-8s %-8s %-8s %-30s%n",
                    "Trade ID", "Account", "Symbol", "Qty", "Side", "Rejection Reason");
            System.out.println(DIV);
            rejected.forEach(t ->
                System.out.printf("  %-10d %-10d %-8s %-8d %-8s %s%n",
                        t.getTradeId(), t.getAccountId(), t.getSymbol(),
                        t.getQuantity(), t.getSide(), t.getRejectionReason())
            );
        }

        System.out.println(LINE);
        System.out.println();
    }
}
