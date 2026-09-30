package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Despesa persistida; amount é apresentado em unidade monetária")
public record TransactionResponse(
        @Schema(format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000") String id,
        @Schema(example = "GROCERIES") String category,
        @Schema(example = "Compras do mês") String description,
        @Schema(example = "125.50") BigDecimal amount,
        @Schema(example = "2026-09-30") LocalDate occurredOn) {
    public static TransactionResponse from(TransactionOutput output) {
        return new TransactionResponse(output.id(), output.category(), output.description(), output.value(),
                output.occurredOn());
    }
}
