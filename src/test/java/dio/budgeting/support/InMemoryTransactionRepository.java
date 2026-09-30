package dio.budgeting.support;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.application.IdempotencyConflictException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class InMemoryTransactionRepository implements TransactionRepository {
    public final List<Transaction> saved = new ArrayList<>();
    private final Map<String, IdempotentResult> idempotentResults = new HashMap<>();

    @Override
    public Transaction save(Transaction transaction) {
        saved.add(transaction);
        return transaction;
    }

    @Override
    public synchronized Transaction saveIdempotently(Transaction transaction, String idempotencyKey,
                                                      String requestFingerprint) {
        var existing = idempotentResults.get(idempotencyKey);
        if (existing != null) {
            if (!existing.fingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException();
            }
            return existing.transaction();
        }

        saved.add(transaction);
        idempotentResults.put(idempotencyKey, new IdempotentResult(requestFingerprint, transaction));
        return transaction;
    }

    @Override
    public List<Transaction> findAllByCategory(Category category) {
        return saved.stream().filter(transaction -> transaction.getCategory() == category).toList();
    }

    @Override
    public List<Transaction> findAllByOccurredOnBetween(LocalDate start, LocalDate end) {
        return saved.stream()
                .filter(transaction -> !transaction.getOccurredOn().isBefore(start)
                        && !transaction.getOccurredOn().isAfter(end))
                .toList();
    }

    @Override
    public List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end) {
        return findAllByOccurredOnBetween(start, end).stream()
                .filter(transaction -> transaction.getCategory() == category)
                .toList();
    }

    private record IdempotentResult(String fingerprint, Transaction transaction) {
    }
}
