package dio.budgeting.application;

import dio.budgeting.application.input.GenerateFinancialSummaryInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class GenerateFinancialSummaryUseCaseTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private final StubTransactionRepository repository = new StubTransactionRepository();
    private final GenerateFinancialSummaryUseCase useCase = new GenerateFinancialSummaryUseCase(repository);

    @Test
    void should_throwInvalidInputAndNotQuery_when_inputIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Financial summary input must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_aggregateTotals_when_periodHasMultipleCategories() {
        repository.result = List.of(
                transaction(Category.GROCERIES, 12550),
                transaction(Category.PHARMA, 4590),
                transaction(Category.GROCERIES, 4000),
                transaction(Category.AUTO, 3000));

        var output = useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(output.total()).isEqualTo(new BigDecimal("241.40"));
        assertThat(output.transactionCount()).isEqualTo(4);
        assertThat(output.totalsByCategory()).containsExactly(
                entry("GROCERIES", new BigDecimal("165.50")),
                entry("PHARMA", new BigDecimal("45.90")),
                entry("AUTO", new BigDecimal("30.00")));
        assertThat(output.topCategory()).isEqualTo("GROCERIES");
        assertThat(output.largestTransaction().value()).isEqualTo(new BigDecimal("125.50"));
        assertThat(output.start()).isEqualTo(START);
        assertThat(output.end()).isEqualTo(END);
    }

    @Test
    void should_returnEmptySummary_when_noTransactionsInPeriod() {
        repository.result = List.of();

        var output = useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(output.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(output.transactionCount()).isZero();
        assertThat(output.totalsByCategory()).isEmpty();
        assertThat(output.topCategory()).isNull();
        assertThat(output.largestTransaction()).isNull();
    }

    @Test
    void should_acceptSingleDayPeriod_when_startEqualsEnd() {
        var day = LocalDate.of(2026, 9, 15);
        repository.result = List.of(transaction(Category.PHARMA, 4590));

        var output = useCase.execute(new GenerateFinancialSummaryInput(day, day));

        assertThat(output.total()).isEqualTo(new BigDecimal("45.90"));
        assertThat(output.topCategory()).isEqualTo("PHARMA");
        assertThat(repository.queriedStart).isEqualTo(day);
        assertThat(repository.queriedEnd).isEqualTo(day);
    }

    @Test
    void should_throwIllegalArgument_when_startIsAfterEnd() {
        var input = new GenerateFinancialSummaryInput(END, START);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must not be after end date");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_throwIllegalArgumentAndNotQuery_when_startIsNull() {
        var input = new GenerateFinancialSummaryInput(null, END);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_throwIllegalArgumentAndNotQuery_when_endIsNull() {
        var input = new GenerateFinancialSummaryInput(START, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_pickFirstDeclaredCategory_when_topTotalsAreTied() {
        // PHARMA comes first in the list, but GROCERIES is declared first in Category
        repository.result = List.of(
                transaction(Category.PHARMA, 5000),
                transaction(Category.AUTO, 1000),
                transaction(Category.GROCERIES, 5000));

        var output = useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(output.topCategory()).isEqualTo("GROCERIES");
    }

    @Test
    void should_queryRepositoryWithPeriod_when_executed() {
        useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(repository.queried).isTrue();
        assertThat(repository.queriedStart).isEqualTo(START);
        assertThat(repository.queriedEnd).isEqualTo(END);
    }

    @Test
    void should_omitCategoriesWithoutTransactions_when_aggregating() {
        repository.result = List.of(transaction(Category.AUTO, 3000));

        var output = useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(output.totalsByCategory())
                .containsOnlyKeys("AUTO")
                .doesNotContainKeys("GROCERIES", "PHARMA");
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0.01",
            "10, 0.10",
            "12345, 123.45",
            "8000, 80.00",
    })
    void should_convertCentsToReaisExactly_when_buildingOutput(long cents, String expectedReais) {
        repository.result = List.of(transaction(Category.GROCERIES, cents));

        var output = useCase.execute(new GenerateFinancialSummaryInput(START, END));

        assertThat(output.total()).isEqualTo(new BigDecimal(expectedReais));
        assertThat(output.totalsByCategory()).containsEntry("GROCERIES", new BigDecimal(expectedReais));
    }

    @Test
    void should_throwArithmeticException_when_totalOverflows() {
        repository.result = List.of(
                transaction(Category.GROCERIES, Long.MAX_VALUE),
                transaction(Category.GROCERIES, 1));

        assertThatThrownBy(() -> useCase.execute(new GenerateFinancialSummaryInput(START, END)))
                .isInstanceOf(ArithmeticException.class);
    }

    private static Transaction transaction(Category category, long amount) {
        return new Transaction("Teste", amount, category, START);
    }

    // Returns a fixed result and records the arguments of the period query.
    static class StubTransactionRepository implements TransactionRepository {
        List<Transaction> result = List.of();
        boolean queried;
        LocalDate queriedStart;
        LocalDate queriedEnd;

        @Override
        public List<Transaction> findAllByOccurredOnBetween(LocalDate start, LocalDate end) {
            this.queried = true;
            this.queriedStart = start;
            this.queriedEnd = end;
            return result;
        }

        @Override
        public Transaction save(Transaction transaction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Transaction saveIdempotently(Transaction transaction, String idempotencyKey,
                                            String requestFingerprint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Transaction> findAllByCategory(Category category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end) {
            throw new UnsupportedOperationException();
        }
    }
}
