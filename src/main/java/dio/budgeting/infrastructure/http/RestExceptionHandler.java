package dio.budgeting.infrastructure.http;

import dio.budgeting.application.InvalidInputException;
import dio.budgeting.application.IdempotencyConflictException;
import dio.budgeting.application.TransactionPersistenceException;
import dio.budgeting.infrastructure.ai.AiProviderException;
import dio.budgeting.infrastructure.ai.AiServiceUnavailableException;
import dio.budgeting.infrastructure.ai.TextToSpeechException;
import dio.budgeting.infrastructure.http.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Arrays;
import java.util.stream.Collectors;

// Messages are written here or come from use case validation; exception details are never exposed.
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(TextToSpeechException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiErrorResponse handleTextToSpeechFailure(TextToSpeechException exception, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.BAD_GATEWAY,
                "Text-to-Speech service failed to generate the audio response", request.getRequestURI());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse handleIdempotencyConflict(IdempotencyConflictException exception,
                                               HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(TransactionPersistenceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiErrorResponse handlePersistenceFailure(TransactionPersistenceException exception,
                                              HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                "Transaction could not be persisted", request.getRequestURI());
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiErrorResponse handleAiUnavailable(AiServiceUnavailableException exception, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AiProviderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ApiErrorResponse handleAiProviderFailure(AiProviderException exception, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.BAD_GATEWAY,
                "Artificial Intelligence service failed to process the request", request.getRequestURI());
    }

    @ExceptionHandler(InvalidInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleInvalidInput(InvalidInputException exception, HttpServletRequest request) {
        return badRequest(exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidAudioUploadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleInvalidAudio(InvalidAudioUploadException exception, HttpServletRequest request) {
        return badRequest(exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedAudioFormatException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    ApiErrorResponse handleUnsupportedAudio(UnsupportedAudioFormatException exception, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({ AudioUploadTooLargeException.class, MaxUploadSizeExceededException.class })
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    ApiErrorResponse handleAudioTooLarge(Exception exception, HttpServletRequest request) {
        var message = exception instanceof AudioUploadTooLargeException
                ? exception.getMessage()
                : "Uploaded file exceeds the maximum allowed size";
        return ApiErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE, message, request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleMissingPart(MissingServletRequestPartException exception, HttpServletRequest request) {
        return badRequest("Required multipart file '%s' is missing".formatted(exception.getRequestPartName()), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiErrorResponse handleUnexpectedIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error", request.getRequestURI());
    }

    // Invalid path or query values, e.g. an unknown Category or start=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        var message = "Invalid value '%s' for parameter '%s'".formatted(exception.getValue(), exception.getName());

        var requiredType = exception.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            message += ". Accepted values: " + Arrays.stream(requiredType.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }

        return badRequest(message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleMissingParameter(MissingServletRequestParameterException exception,
                                            HttpServletRequest request) {
        return badRequest("Required parameter '%s' is missing".formatted(exception.getParameterName()), request);
    }

    // Malformed JSON or values that cannot be read, e.g. an unknown category or an invalid date in the body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleNotReadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return badRequest("Malformed request body", request);
    }

    private static ApiErrorResponse badRequest(String message, HttpServletRequest request) {
        return ApiErrorResponse.of(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }
}
