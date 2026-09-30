package dio.budgeting.application;

import dio.budgeting.application.input.CalculateCategoryTotalInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculateCategoryTotalUseCaseTest {
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private final StubTransactionRepository repository = new StubTransactionRepository();
    private final CalculateCategoryTotalUseCase useCase = new CalculateCategoryTotalUseCase(repository);

    @Test
    void should_throwInvalidInputAndNotQuery_when_inputIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Category total input must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_sumAmountsInCents_when_categoryHasTransactionsInPeriod() {
        repository.result = List.of(
                groceries(12550, LocalDate.of(2026, 9, 15)),
                groceries(4590, LocalDate.of(2026, 9, 28)));

        var output = useCase.execute(new CalculateCategoryTotalInput(Category.GROCERIES, START, END));

        assertThat(output.total()).isEqualTo(new BigDecimal("171.40"));
        assertThat(output.transactionCount()).isEqualTo(2);
        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(output.start()).isEqualTo(START);
        assertThat(output.end()).isEqualTo(END);
    }

    @Test
    void should_returnZero_when_noTransactionsInPeriod() {
        repository.result = List.of();

        var output = useCase.execute(new CalculateCategoryTotalInput(Category.PHARMA, START, END));

        assertThat(output.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(output.transactionCount()).isZero();
    }

    @Test
    void should_acceptSingleDayPeriod_when_startEqualsEnd() {
        var day = LocalDate.of(2026, 9, 15);
        repository.result = List.of(groceries(12550, day));

        var output = useCase.execute(new CalculateCategoryTotalInput(Category.GROCERIES, day, day));

        assertThat(output.total()).isEqualTo(new BigDecimal("125.50"));
        assertThat(repository.queriedStart).isEqualTo(day);
        assertThat(repository.queriedEnd).isEqualTo(day);
    }

    @Test
    void should_throwIllegalArgument_when_startIsAfterEnd() {
        var input = new CalculateCategoryTotalInput(Category.GROCERIES, END, START);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must not be after end date");
        assertThat(repository.queriedCategory).isNull();
    }

    @Test
    void should_throwIllegalArgumentAndNotQuery_when_startIsNull() {
        var input = new CalculateCategoryTotalInput(Category.GROCERIES, null, END);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_throwIllegalArgumentAndNotQuery_when_endIsNull() {
        var input = new CalculateCategoryTotalInput(Category.GROCERIES, START, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_throwIllegalArgumentAndNotQuery_when_categoryIsNull() {
        var input = new CalculateCategoryTotalInput(null, START, END);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category must not be null");
        assertThat(repository.queried).isFalse();
    }

    @Test
    void should_queryRepositoryWithCategoryAndPeriod_when_executed() {
        useCase.execute(new CalculateCategoryTotalInput(Category.AUTO, START, END));

        assertThat(repository.queriedCategory).isEqualTo(Category.AUTO);
        assertThat(repository.queriedStart).isEqualTo(START);
        assertThat(repository.queriedEnd).isEqualTo(END);
    }

    @Test
    void should_countEveryTransaction_when_categoryHasTransactionsInPeriod() {
        repository.result = List.of(
                groceries(1000, START),
                groceries(1, LocalDate.of(2026, 9, 10)),
                groceries(12345, END));

        var output = useCase.execute(new CalculateCategoryTotalInput(Category.GROCERIES, START, END));

        assertThat(output.transactionCount()).isEqualTo(3);
        assertThat(output.total()).isEqualTo(new BigDecimal("133.46"));
    }

    private static Transaction groceries(long amount, LocalDate occurredOn) {
        return new Transaction("Mercado", amount, Category.GROCERIES, occurredOn);
    }

    // Returns a fixed result and records the arguments of the category + period query.
    static class StubTransactionRepository implements TransactionRepository {
        List<Transaction> result = List.of();
        boolean queried;
        Category queriedCategory;
        LocalDate queriedStart;
        LocalDate queriedEnd;

        @Override
        public List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end) {
            this.queried = true;
            this.queriedCategory = category;
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
        public List<Transaction> findAllByOccurredOnBetween(LocalDate start, LocalDate end) {
            throw new UnsupportedOperationException();
        }
    }
}
