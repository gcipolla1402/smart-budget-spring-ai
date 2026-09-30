package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Dados para cadastrar uma despesa")
public record TransactionRequest(
        @Schema(description = "Descrição obrigatória, sem espaços isolados, com até 255 caracteres",
                example = "Compras do mês", maxLength = 255) String description,
        @Schema(description = "Categoria da despesa", example = "GROCERIES",
                allowableValues = { "GROCERIES", "PHARMA", "AUTO" }) Category category,
        @Schema(description = "Valor monetário obrigatório em centavos, maior que zero", example = "12550",
                minimum = "1") Long amount,
        @Schema(description = "Data da despesa no formato ISO-8601", example = "2026-09-30") LocalDate occurredOn) {
    public PersistTransactionInput toInput() {
        return new PersistTransactionInput(description, amount, category, occurredOn);
    }
}
