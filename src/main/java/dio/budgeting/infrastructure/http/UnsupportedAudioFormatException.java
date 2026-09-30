package dio.budgeting.infrastructure.http;

public class UnsupportedAudioFormatException extends RuntimeException {
    public UnsupportedAudioFormatException(String message) {
        super(message);
    }
}
