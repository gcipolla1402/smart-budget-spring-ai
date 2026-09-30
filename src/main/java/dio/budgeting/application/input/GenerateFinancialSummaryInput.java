package dio.budgeting.application.input;

import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record GenerateFinancialSummaryInput(@ToolParam(description = "Data inicial do período, inclusiva (YYYY-MM-DD)") LocalDate start,
                                            @ToolParam(description = "Data final do período, inclusiva (YYYY-MM-DD)") LocalDate end) {
}
