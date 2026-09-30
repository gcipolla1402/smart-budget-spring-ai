package dio.budgeting.application.output;

import dio.budgeting.domain.Category;

import java.math.BigDecimal;

public record CategoryExpenseOutput(String category, BigDecimal total, int transactionCount) {
    public static CategoryExpenseOutput of(Category category, long totalInCents, int transactionCount) {
        return new CategoryExpenseOutput(category.name(), BigDecimal.valueOf(totalInCents, 2), transactionCount);
    }
}
