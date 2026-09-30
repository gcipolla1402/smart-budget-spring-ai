package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.ComparePeriodsUseCase;
import dio.budgeting.application.GenerateMonthlySummaryUseCase;
import dio.budgeting.application.GetExpensesByCategoryUseCase;
import dio.budgeting.application.ListTransactionsByPeriodUseCase;
import dio.budgeting.application.input.ComparePeriodsInput;
import dio.budgeting.application.input.MonthlySummaryInput;
import dio.budgeting.application.input.PeriodInput;
import dio.budgeting.application.output.ExpensesByCategoryOutput;
import dio.budgeting.application.output.FinancialSummaryOutput;
import dio.budgeting.application.output.PeriodComparisonOutput;
import dio.budgeting.application.output.TransactionOutput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinancialQueryTools {
    private final GetExpensesByCategoryUseCase getExpensesByCategoryUseCase;
    private final GenerateMonthlySummaryUseCase generateMonthlySummaryUseCase;
    private final ComparePeriodsUseCase comparePeriodsUseCase;
    private final ListTransactionsByPeriodUseCase listTransactionsByPeriodUseCase;

    public FinancialQueryTools(GetExpensesByCategoryUseCase getExpensesByCategoryUseCase,
                               GenerateMonthlySummaryUseCase generateMonthlySummaryUseCase,
                               ComparePeriodsUseCase comparePeriodsUseCase,
                               ListTransactionsByPeriodUseCase listTransactionsByPeriodUseCase) {
        this.getExpensesByCategoryUseCase = getExpensesByCategoryUseCase;
        this.generateMonthlySummaryUseCase = generateMonthlySummaryUseCase;
        this.comparePeriodsUseCase = comparePeriodsUseCase;
        this.listTransactionsByPeriodUseCase = listTransactionsByPeriodUseCase;
    }

    @Tool(name = "get-expenses-by-category",
            description = "Retorna os gastos de todas as categorias em um período inclusivo, ordenados do maior para o menor. Inclui categorias sem movimentação.")
    public ExpensesByCategoryOutput getExpensesByCategory(PeriodInput input) {
        return getExpensesByCategoryUseCase.execute(input);
    }

    @Tool(name = "get-monthly-summary",
            description = "Retorna o resumo de um mês: total gasto, quantidade, distribuição, maior categoria e maior transação. Valores em reais.")
    public FinancialSummaryOutput getMonthlySummary(MonthlySummaryInput input) {
        return generateMonthlySummaryUseCase.execute(input);
    }

    @Tool(name = "compare-periods",
            description = "Compara os gastos de dois períodos inclusivos. Totais, diferença, percentual e resultado são calculados em Java.")
    public PeriodComparisonOutput comparePeriods(ComparePeriodsInput input) {
        return comparePeriodsUseCase.execute(input);
    }

    @Tool(name = "list-expenses-by-period",
            description = "Lista as transações entre duas datas inclusivas, ordenadas por data.")
    public List<TransactionOutput> listExpensesByPeriod(PeriodInput input) {
        return listTransactionsByPeriodUseCase.execute(input);
    }
}
