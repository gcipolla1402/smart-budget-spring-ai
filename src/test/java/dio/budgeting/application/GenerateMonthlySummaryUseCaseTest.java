package dio.budgeting.application;

import dio.budgeting.application.input.MonthlySummaryInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateMonthlySummaryUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final GenerateMonthlySummaryUseCase useCase = new GenerateMonthlySummaryUseCase(
            new GenerateFinancialSummaryUseCase(repository));

    @Test
    void should_returnEmptyMonthlySummary() {
        var output = useCase.execute(new MonthlySummaryInput(2026, 2));

        assertThat(output.start()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(output.end()).isEqualTo(LocalDate.parse("2026-02-28"));
        assertThat(output.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(output.transactionCount()).isZero();
        assertThat(output.largestTransaction()).isNull();
    }

    @Test
    void should_summarizeSingleTransactionAndExposeItAsLargest() {
        save("Farmácia", 4590, Category.PHARMA, "2026-09-30");

        var output = useCase.execute(new MonthlySummaryInput(2026, 9));

        assertThat(output.total()).isEqualTo(new BigDecimal("45.90"));
        assertThat(output.topCategory()).isEqualTo("PHARMA");
        assertThat(output.largestTransaction().description()).isEqualTo("Farmácia");
        assertThat(output.largestTransaction().value()).isEqualTo(new BigDecimal("45.90"));
    }

    @Test
    void should_summarizeMultipleCategoriesAndPickLargestTransaction() {
        save("Mercado", 5000, Category.GROCERIES, "2026-09-01");
        save("Carro", 12000, Category.AUTO, "2026-09-15");
        save("Remédio", 4000, Category.PHARMA, "2026-09-30");
        save("Fora do mês", 99999, Category.AUTO, "2026-10-01");

        var output = useCase.execute(new MonthlySummaryInput(2026, 9));

        assertThat(output.total()).isEqualTo(new BigDecimal("210.00"));
        assertThat(output.transactionCount()).isEqualTo(3);
        assertThat(output.topCategory()).isEqualTo("AUTO");
        assertThat(output.largestTransaction().description()).isEqualTo("Carro");
    }

    @Test
    void should_rejectInvalidMonth() {
        assertThatThrownBy(() -> useCase.execute(new MonthlySummaryInput(2026, 13)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Year and month must define a valid month");
    }

    private void save(String description, long amount, Category category, String date) {
        repository.save(new Transaction(description, amount, category, LocalDate.parse(date)));
    }
}
