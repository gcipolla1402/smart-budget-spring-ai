package dio.budgeting.infrastructure.http.response;

import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resposta JSON padronizada para erros")
public record ApiErrorResponse(
        @Schema(example = "2026-09-30T12:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Invalid request") String message,
        @Schema(example = "/transactions") String path) {
    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
