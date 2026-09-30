package dio.budgeting.infrastructure.http;

import dio.budgeting.application.InvalidInputException;

import java.util.Locale;

public enum AiResponseFormat {
    TEXT,
    AUDIO;

    public static AiResponseFormat from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidInputException("responseFormat must be 'text' or 'audio'");
        }
    }
}
