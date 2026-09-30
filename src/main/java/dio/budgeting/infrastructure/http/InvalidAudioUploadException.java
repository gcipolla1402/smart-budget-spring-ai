package dio.budgeting.infrastructure.http;

public class InvalidAudioUploadException extends RuntimeException {
    public InvalidAudioUploadException(String message) {
        super(message);
    }
}
