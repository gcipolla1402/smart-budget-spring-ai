package dio.budgeting.application;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key has already been used with different transaction data");
    }
}
