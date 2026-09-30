package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = Transaction.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false)
    @Check(constraints = "amount > 0")
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false)
    private LocalDate occurredOn;

    public static TransactionEntity from(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId().uuid(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getOccurredOn());
    }

    public Transaction toDomain() {
        return new Transaction(
                new TransactionId(this.id),
                this.description,
                this.amount,
                this.category,
                this.occurredOn
        );
    }
}
