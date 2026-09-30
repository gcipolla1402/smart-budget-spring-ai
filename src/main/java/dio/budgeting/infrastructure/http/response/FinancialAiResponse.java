package dio.budgeting.infrastructure.http.response;

import dio.budgeting.infrastructure.ai.FinancialAiResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta textual do fluxo de IA")
public record FinancialAiResponse(
        @Schema(description = "Texto reconhecido no áudio", example = "Registre 80 reais de mercado") String transcription,
        @Schema(description = "Resposta textual produzida após a execução das Tools",
                example = "Despesa registrada com sucesso") String response,
        @Schema(example = "text", allowableValues = "text") String responseFormat,
        @Schema(description = "UUID da transação, quando o comando criou uma despesa", format = "uuid",
                example = "550e8400-e29b-41d4-a716-446655440000") String transactionId) {
    public static FinancialAiResponse from(FinancialAiResult result) {
        return new FinancialAiResponse(
                result.transcription(), result.response(), "text", result.transactionId());
    }
}
