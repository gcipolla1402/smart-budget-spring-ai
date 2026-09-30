package dio.budgeting.application.output;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpensesByCategoryOutput(LocalDate start, LocalDate end, BigDecimal total,
                                       List<CategoryExpenseOutput> categories) {
    public static ExpensesByCategoryOutput of(LocalDate start, LocalDate end, long totalInCents,
                                              List<CategoryExpenseOutput> categories) {
        return new ExpensesByCategoryOutput(start, end, BigDecimal.valueOf(totalInCents, 2), List.copyOf(categories));
    }
}
