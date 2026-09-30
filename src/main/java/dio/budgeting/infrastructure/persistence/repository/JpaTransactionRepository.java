package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.application.IdempotencyConflictException;
import dio.budgeting.application.TransactionPersistenceException;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final TransactionEntityRepository transactionEntityRepository;
    private final TransactionIdempotencyEntityRepository idempotencyEntityRepository;

    public JpaTransactionRepository(TransactionEntityRepository transactionEntityRepository,
                                    TransactionIdempotencyEntityRepository idempotencyEntityRepository) {
        this.transactionEntityRepository = transactionEntityRepository;
        this.idempotencyEntityRepository = idempotencyEntityRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        var entity = TransactionEntity.from(transaction);
        return transactionEntityRepository.save(entity).toDomain();
    }

    @Override
    @Transactional
    public Transaction saveIdempotently(Transaction transaction, String idempotencyKey, String requestFingerprint) {
        try {
            idempotencyEntityRepository.claim(idempotencyKey, requestFingerprint);
            var record = idempotencyEntityRepository.findByIdForUpdate(idempotencyKey).orElseThrow();

            if (!record.getFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException();
            }
            if (record.getTransaction() != null) {
                return record.getTransaction().toDomain();
            }

            var entity = transactionEntityRepository.save(TransactionEntity.from(transaction));
            record.setTransaction(entity);
            idempotencyEntityRepository.save(record);
            return entity.toDomain();
        }
        catch (IdempotencyConflictException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new TransactionPersistenceException(exception);
        }
    }

    @Override
    public List<Transaction> findAllByCategory(Category category) {
        return transactionEntityRepository.findAllByCategory(category)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAllByOccurredOnBetween(LocalDate start, LocalDate end) {
        return transactionEntityRepository.findAllByOccurredOnBetween(start, end)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end) {
        return transactionEntityRepository.findAllByCategoryAndOccurredOnBetween(category, start, end)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }
}
