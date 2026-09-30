package dio.budgeting.application.output;

import dio.budgeting.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CategoryTotalOutput(String category, LocalDate start, LocalDate end, BigDecimal total,
                                  int transactionCount) {
    private static final int CENTS_SCALE = 2;

    public static CategoryTotalOutput of(Category category, LocalDate start, LocalDate end,
                                         long totalInCents, int transactionCount) {
        return new CategoryTotalOutput(
                category.name(),
                start,
                end,
                BigDecimal.valueOf(totalInCents, CENTS_SCALE),
                transactionCount);
    }
}
