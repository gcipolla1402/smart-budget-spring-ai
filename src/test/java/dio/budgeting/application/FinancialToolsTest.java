package dio.budgeting.application;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.infrastructure.ai.FinancialQueryTools;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Builds the tool callbacks the same way ChatClient.defaultTools(...) does and calls them with
// JSON arguments, as the model would. No OpenAI call is made.
class FinancialToolsTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final GenerateFinancialSummaryUseCase summaryUseCase = new GenerateFinancialSummaryUseCase(repository);
    private final ToolCallback[] tools = ToolCallbacks.from(
            new CalculateCategoryTotalUseCase(repository),
            summaryUseCase,
            new FinancialQueryTools(
                    new GetExpensesByCategoryUseCase(repository),
                    new GenerateMonthlySummaryUseCase(summaryUseCase),
                    new ComparePeriodsUseCase(repository),
                    new ListTransactionsByPeriodUseCase(repository)));

    @Test
    void should_exposeStableToolNames_when_buildingCallbacks() {
        assertThat(Arrays.stream(tools).map(tool -> tool.getToolDefinition().name()))
                .containsExactlyInAnyOrder("calculate-category-total", "generate-financial-summary",
                        "get-expenses-by-category", "get-monthly-summary", "compare-periods",
                        "list-expenses-by-period");
    }

    @Test
    void should_describeParametersForTheModel_when_buildingSchemas() {
        var categoryTotal = tool("calculate-category-total").getToolDefinition();
        var summary = tool("generate-financial-summary").getToolDefinition();

        assertThat(categoryTotal.description()).contains("UMA categoria", "período");
        assertThat(categoryTotal.inputSchema()).contains("category", "start", "end", "YYYY-MM-DD", "inclusiva",
                "GROCERIES", "PHARMA", "AUTO");
        assertThat(summary.description()).contains("resumo financeiro", "categoria com maior gasto");
        assertThat(summary.inputSchema()).contains("start", "end", "YYYY-MM-DD", "inclusiva");
    }

    @Test
    void should_calculateCategoryTotalInJava_when_toolIsCalledWithJsonArguments() {
        save(Category.GROCERIES, 12550, LocalDate.of(2026, 9, 15));
        save(Category.GROCERIES, 4590, LocalDate.of(2026, 9, 28));
        save(Category.GROCERIES, 9999, LocalDate.of(2026, 10, 1));

        var result = tool("calculate-category-total").call("""
                {"input": {"category": "GROCERIES", "start": "2026-09-01", "end": "2026-09-30"}}
                """);

        assertThat(result).contains("\"total\":171.40", "\"transactionCount\":2", "\"category\":\"GROCERIES\"");
    }

    @Test
    void should_generateSummaryInJava_when_toolIsCalledWithJsonArguments() {
        save(Category.GROCERIES, 12550, LocalDate.of(2026, 9, 15));
        save(Category.PHARMA, 4590, LocalDate.of(2026, 9, 28));

        var result = tool("generate-financial-summary").call("""
                {"input": {"start": "2026-09-01", "end": "2026-09-30"}}
                """);

        assertThat(result).contains("\"total\":171.40", "\"transactionCount\":2", "\"topCategory\":\"GROCERIES\"",
                "\"GROCERIES\":125.50", "\"PHARMA\":45.90");
    }

    @Test
    void should_callNewUseCasesThroughTools() {
        save(Category.GROCERIES, 10000, LocalDate.of(2026, 8, 10));
        save(Category.AUTO, 12500, LocalDate.of(2026, 9, 15));

        assertThat(tool("get-expenses-by-category").call("""
                {"input":{"start":"2026-09-01","end":"2026-09-30"}}
                """)).contains("\"AUTO\"", "125.00", "\"GROCERIES\"", "0.00");
        assertThat(tool("get-monthly-summary").call("""
                {"input":{"year":2026,"month":9}}
                """)).contains("\"total\":125.00", "\"largestTransaction\"");
        assertThat(tool("compare-periods").call("""
                {"input":{"firstStart":"2026-08-01","firstEnd":"2026-08-31",
                 "secondStart":"2026-09-01","secondEnd":"2026-09-30"}}
                """)).contains("\"difference\":25.00", "\"result\":\"SECOND_HIGHER\"");
        assertThat(tool("list-expenses-by-period").call("""
                {"input":{"start":"2026-09-01","end":"2026-09-30"}}
                """)).contains("\"category\":\"AUTO\"", "\"value\":125.00");
    }

    @Test
    void should_returnToolErrorForInvalidNewToolArguments() {
        assertThatThrownBy(() -> tool("compare-periods").call("""
                    {"input":{"firstStart":"2026-08-31","firstEnd":"2026-08-01",
                     "secondStart":"2026-09-01","secondEnd":"2026-09-30"}}
                    """))
                .isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class)
                .hasRootCauseMessage("First start date must not be after end date");
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(tools)
                .filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void save(Category category, long amount, LocalDate occurredOn) {
        repository.save(new Transaction("Teste", amount, category, occurredOn));
    }
}
