package dio.budgeting.application.input;

import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record ComparePeriodsInput(
        @ToolParam(description = "Data inicial inclusiva do primeiro período (YYYY-MM-DD)") LocalDate firstStart,
        @ToolParam(description = "Data final inclusiva do primeiro período (YYYY-MM-DD)") LocalDate firstEnd,
        @ToolParam(description = "Data inicial inclusiva do segundo período (YYYY-MM-DD)") LocalDate secondStart,
        @ToolParam(description = "Data final inclusiva do segundo período (YYYY-MM-DD)") LocalDate secondEnd) {
}
