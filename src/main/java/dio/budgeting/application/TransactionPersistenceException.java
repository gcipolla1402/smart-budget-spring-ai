package dio.budgeting.application;

public class TransactionPersistenceException extends RuntimeException {
    public TransactionPersistenceException(Throwable cause) {
        super("Transaction could not be persisted", cause);
    }
}
