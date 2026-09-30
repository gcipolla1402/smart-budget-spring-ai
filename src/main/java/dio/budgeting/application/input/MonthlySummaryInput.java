package dio.budgeting.application.input;

import org.springframework.ai.tool.annotation.ToolParam;

public record MonthlySummaryInput(
        @ToolParam(description = "Ano com quatro dígitos") Integer year,
        @ToolParam(description = "Mês de 1 a 12") Integer month) {
}
