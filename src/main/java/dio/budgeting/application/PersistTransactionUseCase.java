package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PersistTransactionUseCase {
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public PersistTransactionUseCase(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        return execute(input, null);
    }

    public TransactionOutput execute(PersistTransactionInput input, String idempotencyKey) {
        validate(input);
        validateIdempotencyKey(idempotencyKey);

        var occurredOn = Objects.requireNonNullElseGet(input.occurredOn(), () -> LocalDate.now(clock));
        var transaction = new Transaction(input.description(), input.amount(), input.category(), occurredOn);

        var savedTransaction = idempotencyKey == null
                ? transactionRepository.save(transaction)
                : transactionRepository.saveIdempotently(transaction, idempotencyKey, fingerprint(input));

        return TransactionOutput.from(savedTransaction);
    }

    // Runs for every caller (REST and tool calling), so invalid data never reaches the repository.
    private static void validate(PersistTransactionInput input) {
        if (input == null) {
            throw new InvalidInputException("Transaction input must not be null");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw new InvalidInputException("Description must not be blank");
        }
        if (input.description().length() > Transaction.DESCRIPTION_MAX_LENGTH) {
            throw new InvalidInputException(
                    "Description must not exceed %d characters".formatted(Transaction.DESCRIPTION_MAX_LENGTH));
        }
        if (input.category() == null) {
            throw new InvalidInputException("Category must not be null");
        }
        if (input.amount() == null) {
            throw new InvalidInputException("Amount must not be null");
        }
        if (input.amount() <= 0) {
            throw new InvalidInputException("Amount must be greater than zero");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        if (idempotencyKey.isBlank()) {
            throw new InvalidInputException("Idempotency-Key must not be blank");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new InvalidInputException("Idempotency-Key must not exceed 128 characters");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new InvalidInputException("Idempotency-Key contains invalid characters");
        }
    }

    private static String fingerprint(PersistTransactionInput input) {
        var date = input.occurredOn() == null ? "<absent>" : input.occurredOn().toString();
        var canonical = "%d:%s|%d|%s|%s".formatted(
                input.description().length(), input.description(), input.amount(), input.category().name(), date);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
