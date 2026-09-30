package dio.budgeting.infrastructure.ai;

public class TextToSpeechException extends RuntimeException {
    public TextToSpeechException(Throwable cause) {
        super("Text-to-Speech service failed to generate the audio response", cause);
    }

    public TextToSpeechException() {
        super("Text-to-Speech service is not configured in this environment");
    }
}
