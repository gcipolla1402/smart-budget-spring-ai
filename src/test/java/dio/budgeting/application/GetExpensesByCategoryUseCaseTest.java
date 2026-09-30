package dio.budgeting.application;

import dio.budgeting.application.input.PeriodInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetExpensesByCategoryUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final GetExpensesByCategoryUseCase useCase = new GetExpensesByCategoryUseCase(repository);

    @Test
    void should_orderCategoriesByTotalAndIncludeCategoriesWithoutMovement() {
        save(Category.GROCERIES, 5000, "2026-09-01");
        save(Category.AUTO, 8000, "2026-09-30");
        save(Category.GROCERIES, 4000, "2026-09-15");
        save(Category.PHARMA, 99999, "2026-10-01");

        var output = useCase.execute(period("2026-09-01", "2026-09-30"));

        assertThat(output.total()).isEqualTo(new BigDecimal("170.00"));
        assertThat(output.categories()).extracting(category -> category.category())
                .containsExactly("GROCERIES", "AUTO", "PHARMA");
        assertThat(output.categories()).extracting(category -> category.total())
                .containsExactly(new BigDecimal("90.00"), new BigDecimal("80.00"), new BigDecimal("0.00"));
        assertThat(output.categories()).extracting(category -> category.transactionCount())
                .containsExactly(2, 1, 0);
    }

    @Test
    void should_useCategoryDeclarationOrderToBreakEqualTotals() {
        save(Category.AUTO, 1000, "2026-09-01");
        save(Category.GROCERIES, 1000, "2026-09-01");

        var output = useCase.execute(period("2026-09-01", "2026-09-30"));

        assertThat(output.categories()).extracting(category -> category.category())
                .containsExactly("GROCERIES", "AUTO", "PHARMA");
    }

    @Test
    void should_rejectInvalidPeriod() {
        assertThatThrownBy(() -> useCase.execute(period("2026-09-30", "2026-09-01")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Start date must not be after end date");
    }

    private void save(Category category, long amount, String date) {
        repository.save(new Transaction("Teste", amount, category, LocalDate.parse(date)));
    }

    private static PeriodInput period(String start, String end) {
        return new PeriodInput(LocalDate.parse(start), LocalDate.parse(end));
    }
}
