package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.FinancialSummaryOutput;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import dio.budgeting.application.output.TransactionOutput;

public record FinancialSummaryResponse(LocalDate start, LocalDate end, BigDecimal total, int transactionCount,
                                       Map<String, BigDecimal> totalsByCategory, String topCategory,
                                       TransactionOutput largestTransaction) {
    public static FinancialSummaryResponse from(FinancialSummaryOutput output) {
        return new FinancialSummaryResponse(output.start(), output.end(), output.total(), output.transactionCount(),
                output.totalsByCategory(), output.topCategory(), output.largestTransaction());
    }
}
