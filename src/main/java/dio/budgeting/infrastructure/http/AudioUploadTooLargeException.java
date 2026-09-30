package dio.budgeting.infrastructure.http;

public class AudioUploadTooLargeException extends RuntimeException {
    public AudioUploadTooLargeException(String message) {
        super(message);
    }
}
