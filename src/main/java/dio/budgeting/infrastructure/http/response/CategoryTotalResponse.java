package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.CategoryTotalOutput;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Total gasto por categoria em um período inclusivo")
public record CategoryTotalResponse(
        @Schema(example = "GROCERIES") String category,
        @Schema(example = "2026-09-01") LocalDate start,
        @Schema(example = "2026-09-30") LocalDate end,
        @Schema(example = "350.75") BigDecimal total,
        @Schema(example = "3") int transactionCount) {
    public static CategoryTotalResponse from(CategoryTotalOutput output) {
        return new CategoryTotalResponse(output.category(), output.start(), output.end(), output.total(),
                output.transactionCount());
    }
}
