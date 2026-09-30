package dio.budgeting.infrastructure.ai;

public class AiProviderException extends RuntimeException {
    public AiProviderException(Throwable cause) {
        super("Artificial Intelligence provider failed to process the request", cause);
    }
}
