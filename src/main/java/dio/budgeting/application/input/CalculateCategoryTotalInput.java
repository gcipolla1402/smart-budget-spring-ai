package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record CalculateCategoryTotalInput(@ToolParam(description = "Categoria financeira dos gastos") Category category,
                                          @ToolParam(description = "Data inicial do período, inclusiva (YYYY-MM-DD)") LocalDate start,
                                          @ToolParam(description = "Data final do período, inclusiva (YYYY-MM-DD)") LocalDate end) {
}
