package dio.budgeting.application.input;

import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record PeriodInput(
        @ToolParam(description = "Data inicial inclusiva (YYYY-MM-DD)") LocalDate start,
        @ToolParam(description = "Data final inclusiva (YYYY-MM-DD)") LocalDate end) {
}
