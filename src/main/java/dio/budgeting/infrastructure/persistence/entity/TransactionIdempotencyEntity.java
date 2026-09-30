package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.application.PersistTransactionUseCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transaction_idempotency")
@Getter
@Setter
@NoArgsConstructor
public class TransactionIdempotencyEntity {
    @Id
    @Column(name = "idempotency_key", length = PersistTransactionUseCase.IDEMPOTENCY_KEY_MAX_LENGTH)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private TransactionEntity transaction;
}
