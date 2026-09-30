package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionEntityTest {
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 9, 15);

    @Test
    void should_copyAllFields_when_convertingFromDomain() {
        var transaction = new Transaction("Mercado", 8000, Category.GROCERIES, OCCURRED_ON);

        var entity = TransactionEntity.from(transaction);

        assertThat(entity.getId()).isEqualTo(transaction.getId().uuid());
        assertThat(entity.getDescription()).isEqualTo("Mercado");
        assertThat(entity.getAmount()).isEqualTo(8000);
        assertThat(entity.getCategory()).isEqualTo(Category.GROCERIES);
        assertThat(entity.getOccurredOn()).isEqualTo(OCCURRED_ON);
    }

    @Test
    void should_copyAllFields_when_convertingToDomain() {
        var id = UUID.randomUUID();
        var entity = new TransactionEntity(id, "Posto", 12345, Category.AUTO, OCCURRED_ON);

        var transaction = entity.toDomain();

        assertThat(transaction.getId().uuid()).isEqualTo(id);
        assertThat(transaction.getDescription()).isEqualTo("Posto");
        assertThat(transaction.getAmount()).isEqualTo(12345);
        assertThat(transaction.getCategory()).isEqualTo(Category.AUTO);
        assertThat(transaction.getOccurredOn()).isEqualTo(OCCURRED_ON);
    }
}
