package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final PersistTransactionUseCase useCase = new PersistTransactionUseCase(
            repository, Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), SAO_PAULO));

    @Test
    void should_rejectAndNotSave_when_inputIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Transaction input must not be null");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_persistAllFields_when_inputIsValid() {
        var occurredOn = LocalDate.of(2026, 9, 15);

        var output = useCase.execute(new PersistTransactionInput("Mercado", 1L, Category.GROCERIES, occurredOn));

        assertThat(output.value()).isEqualTo(new BigDecimal("0.01"));
        assertThat(repository.saved).singleElement().satisfies(transaction -> {
            assertThat(transaction.getDescription()).isEqualTo("Mercado");
            assertThat(transaction.getAmount()).isEqualTo(1);
            assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
            assertThat(transaction.getOccurredOn()).isEqualTo(occurredOn);
        });
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void should_rejectAndNotSave_when_amountIsNotPositive(long amount) {
        var input = new PersistTransactionInput("Mercado", amount, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be greater than zero");
        assertThat(repository.saved).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t\n"})
    void should_rejectAndNotSave_when_descriptionIsBlank(String description) {
        var input = new PersistTransactionInput(description, 8000L, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Description must not be blank");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_rejectAndNotSave_when_descriptionExceedsDatabaseLimit() {
        var input = new PersistTransactionInput("x".repeat(256), 8000L, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Description must not exceed 255 characters");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_rejectAndNotSave_when_amountIsNull() {
        var input = new PersistTransactionInput("Mercado", null, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Amount must not be null");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_rejectAndNotSave_when_categoryIsNull() {
        var input = new PersistTransactionInput("Mercado", 8000L, null, null);

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category must not be null");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_keepInformedDate_when_occurredOnIsProvided() {
        var useCase = new PersistTransactionUseCase(repository, Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), SAO_PAULO));
        var informedDate = LocalDate.of(2026, 9, 15);

        var output = useCase.execute(new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, informedDate));

        assertThat(output.occurredOn()).isEqualTo(informedDate);
        assertThat(repository.saved).singleElement()
                .extracting(Transaction::getOccurredOn).isEqualTo(informedDate);
    }

    @Test
    void should_useCurrentDateFromClock_when_occurredOnIsNotProvided() {
        var useCase = new PersistTransactionUseCase(repository, Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), SAO_PAULO));

        var output = useCase.execute(new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null));

        assertThat(output.occurredOn()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(repository.saved).singleElement()
                .extracting(Transaction::getOccurredOn).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void should_useClockZone_when_utcDateIsAlreadyTheNextDay() {
        // 01:00 UTC on Sep 29 is still 22:00 on Sep 28 in São Paulo
        var useCase = new PersistTransactionUseCase(repository, Clock.fixed(Instant.parse("2026-09-29T01:00:00Z"), SAO_PAULO));

        var output = useCase.execute(new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null));

        assertThat(output.occurredOn()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void should_returnOriginalTransaction_when_sameKeyAndInputAreRepeated() {
        var input = new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null);

        var first = useCase.execute(input, "operation-1");
        var repeated = useCase.execute(input, "operation-1");

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_reject_when_sameKeyIsReusedWithDifferentInput() {
        useCase.execute(new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null), "operation-1");

        assertThatThrownBy(() -> useCase.execute(
                new PersistTransactionInput("Farmácia", 8000L, Category.PHARMA, null), "operation-1"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key has already been used with different transaction data");
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_createTwoTransactions_when_keysAreDifferent() {
        var input = new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null);

        var first = useCase.execute(input, "operation-1");
        var second = useCase.execute(input, "operation-2");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(repository.saved).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid key", "invalid/key", "á"})
    void should_rejectInvalidIdempotencyKeyBeforeSaving(String key) {
        var input = new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input, key))
                .isInstanceOf(InvalidInputException.class);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_rejectIdempotencyKeyAboveMaximumLength() {
        var input = new PersistTransactionInput("Mercado", 8000L, Category.GROCERIES, null);

        assertThatThrownBy(() -> useCase.execute(input, "k".repeat(129)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Idempotency-Key must not exceed 128 characters");
        assertThat(repository.saved).isEmpty();
    }
}
