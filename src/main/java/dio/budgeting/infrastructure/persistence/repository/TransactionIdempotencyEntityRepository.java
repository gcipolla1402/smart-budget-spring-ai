package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.TransactionIdempotencyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionIdempotencyEntityRepository
        extends CrudRepository<TransactionIdempotencyEntity, String> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO transaction_idempotency (idempotency_key, fingerprint)
            VALUES (:idempotencyKey, :fingerprint)
            """, nativeQuery = true)
    int claim(@Param("idempotencyKey") String idempotencyKey, @Param("fingerprint") String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT record FROM TransactionIdempotencyEntity record WHERE record.idempotencyKey = :idempotencyKey")
    Optional<TransactionIdempotencyEntity> findByIdForUpdate(@Param("idempotencyKey") String idempotencyKey);
}
