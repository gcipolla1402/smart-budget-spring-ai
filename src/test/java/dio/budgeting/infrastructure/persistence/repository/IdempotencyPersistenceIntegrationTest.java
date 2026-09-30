package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.application.IdempotencyConflictException;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotencyPersistenceIntegrationTest {
    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    TransactionIdempotencyEntityRepository idempotencyRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void should_persistIdempotencyRecordAndReturnOriginalTransaction() {
        var key = "persistence-" + UUID.randomUUID();
        var fingerprint = "a".repeat(64);

        var first = transactionRepository.saveIdempotently(transaction(), key, fingerprint);
        var repeated = transactionRepository.saveIdempotently(transaction(), key, fingerprint);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(idempotencyRepository.findById(key)).isPresent()
                .get().extracting(record -> record.getFingerprint()).isEqualTo(fingerprint);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_idempotency WHERE idempotency_key = ? AND transaction_id IS NOT NULL",
                Integer.class, key)).isEqualTo(1);
    }

    @Test
    void should_rejectDifferentFingerprintForPersistedKey() {
        var key = "conflict-" + UUID.randomUUID();
        transactionRepository.saveIdempotently(transaction(), key, "a".repeat(64));

        assertThatThrownBy(() -> transactionRepository.saveIdempotently(transaction(), key, "b".repeat(64)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void should_enforceUniqueIdempotencyKeyInDatabase() {
        var key = "unique-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transaction_idempotency (idempotency_key, fingerprint) VALUES (?, ?)",
                key, "a".repeat(64));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO transaction_idempotency (idempotency_key, fingerprint) VALUES (?, ?)",
                key, "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_createOnlyOneTransaction_when_sameKeyIsProcessedConcurrently() throws Exception {
        var key = "concurrent-" + UUID.randomUUID();
        var fingerprint = "c".repeat(64);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> persistAfterSignal(key, fingerprint, ready, start));
            var secondFuture = executor.submit(() -> persistAfterSignal(key, fingerprint, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var first = firstFuture.get(10, TimeUnit.SECONDS);
            var second = secondFuture.get(10, TimeUnit.SECONDS);

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transaction_idempotency WHERE idempotency_key = ?",
                    Integer.class, key)).isEqualTo(1);
        }
    }

    private Transaction persistAfterSignal(String key, String fingerprint, CountDownLatch ready,
                                           CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return transactionRepository.saveIdempotently(transaction(), key, fingerprint);
    }

    private static Transaction transaction() {
        return new Transaction("Operação idempotente", 12345, Category.GROCERIES,
                LocalDate.of(2099, 8, 15));
    }
}
