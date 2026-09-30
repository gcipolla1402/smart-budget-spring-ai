package dio.budgeting.application;

import dio.budgeting.application.input.ComparePeriodsInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparePeriodsUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final ComparePeriodsUseCase useCase = new ComparePeriodsUseCase(repository);

    @Test
    void should_compareTwoPeriodsAndCalculateChangeInJava() {
        save(10000, "2026-08-10");
        save(12500, "2026-09-10");

        var output = useCase.execute(input("2026-08-01", "2026-08-31", "2026-09-01", "2026-09-30"));

        assertThat(output.firstTotal()).isEqualTo(new BigDecimal("100.00"));
        assertThat(output.secondTotal()).isEqualTo(new BigDecimal("125.00"));
        assertThat(output.difference()).isEqualTo(new BigDecimal("25.00"));
        assertThat(output.percentageChange()).isEqualTo(new BigDecimal("25.00"));
        assertThat(output.result()).isEqualTo("SECOND_HIGHER");
    }

    @Test
    void should_reportTieBetweenPeriods() {
        save(8000, "2026-08-01");
        save(8000, "2026-09-30");

        var output = useCase.execute(input("2026-08-01", "2026-08-31", "2026-09-01", "2026-09-30"));

        assertThat(output.difference()).isEqualTo(new BigDecimal("0.00"));
        assertThat(output.percentageChange()).isEqualTo(new BigDecimal("0.00"));
        assertThat(output.result()).isEqualTo("EQUAL");
    }

    @Test
    void should_rejectInvalidSecondPeriod() {
        assertThatThrownBy(() -> useCase.execute(
                input("2026-08-01", "2026-08-31", "2026-09-30", "2026-09-01")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Second start date must not be after end date");
    }

    private void save(long amount, String date) {
        repository.save(new Transaction("Teste", amount, Category.GROCERIES, LocalDate.parse(date)));
    }

    private static ComparePeriodsInput input(String firstStart, String firstEnd, String secondStart, String secondEnd) {
        return new ComparePeriodsInput(LocalDate.parse(firstStart), LocalDate.parse(firstEnd),
                LocalDate.parse(secondStart), LocalDate.parse(secondEnd));
    }
}
