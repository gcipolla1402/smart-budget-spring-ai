package dio.budgeting.application.output;

import dio.budgeting.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

// totalsByCategory only contains categories with transactions in the period, in Category declaration order.
// topCategory is null when the period has no transactions.
public record FinancialSummaryOutput(LocalDate start, LocalDate end, BigDecimal total, int transactionCount,
                                     Map<String, BigDecimal> totalsByCategory, String topCategory,
                                     TransactionOutput largestTransaction) {
    private static final int CENTS_SCALE = 2;

    public static FinancialSummaryOutput of(LocalDate start, LocalDate end, long totalInCents, int transactionCount,
                                            EnumMap<Category, Long> totalsByCategoryInCents, Category topCategory) {
        return of(start, end, totalInCents, transactionCount, totalsByCategoryInCents, topCategory, null);
    }

    public static FinancialSummaryOutput of(LocalDate start, LocalDate end, long totalInCents, int transactionCount,
                                            EnumMap<Category, Long> totalsByCategoryInCents, Category topCategory,
                                            TransactionOutput largestTransaction) {
        var totalsByCategory = new LinkedHashMap<String, BigDecimal>();
        totalsByCategoryInCents.forEach((category, cents) ->
                totalsByCategory.put(category.name(), BigDecimal.valueOf(cents, CENTS_SCALE)));

        return new FinancialSummaryOutput(
                start,
                end,
                BigDecimal.valueOf(totalInCents, CENTS_SCALE),
                transactionCount,
                Collections.unmodifiableMap(totalsByCategory),
                topCategory == null ? null : topCategory.name(),
                largestTransaction);
    }
}
