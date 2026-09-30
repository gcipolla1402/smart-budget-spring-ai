package dio.budgeting.domain;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);

    Transaction saveIdempotently(Transaction transaction, String idempotencyKey, String requestFingerprint);

    List<Transaction> findAllByCategory(Category category);

    // start and end are inclusive
    List<Transaction> findAllByOccurredOnBetween(LocalDate start, LocalDate end);

    // start and end are inclusive
    List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end);
}
