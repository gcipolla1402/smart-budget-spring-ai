package dio.budgeting.application;

import dio.budgeting.application.input.GenerateFinancialSummaryInput;
import dio.budgeting.application.output.FinancialSummaryOutput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Comparator;

@Service
public class GenerateFinancialSummaryUseCase {
    private final TransactionRepository transactionRepository;

    public GenerateFinancialSummaryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "generate-financial-summary",
            description = "Gera o resumo financeiro de um período: total gasto, quantidade de transações, "
                    + "total por categoria e categoria com maior gasto. Use para perguntas sobre os gastos gerais "
                    + "de um período ou sobre qual categoria teve mais gastos. Valores retornados em reais.")
    public FinancialSummaryOutput execute(GenerateFinancialSummaryInput input) {
        if (input == null) {
            throw new InvalidInputException("Financial summary input must not be null");
        }
        if (input.start() == null) {
            throw new InvalidInputException("Start date must not be null");
        }
        if (input.end() == null) {
            throw new InvalidInputException("End date must not be null");
        }
        if (input.start().isAfter(input.end())) {
            throw new InvalidInputException("Start date must not be after end date");
        }

        var transactions = transactionRepository.findAllByOccurredOnBetween(input.start(), input.end());

        long totalInCents = 0L;
        var totalsByCategoryInCents = new EnumMap<Category, Long>(Category.class);
        for (var transaction : transactions) {
            totalInCents = Math.addExact(totalInCents, transaction.getAmount());
            totalsByCategoryInCents.merge(transaction.getCategory(), transaction.getAmount(), Math::addExact);
        }

        return FinancialSummaryOutput.of(input.start(), input.end(), totalInCents, transactions.size(),
                totalsByCategoryInCents, findTopCategory(totalsByCategoryInCents),
                findLargestTransaction(transactions));
    }

    private static TransactionOutput findLargestTransaction(java.util.List<dio.budgeting.domain.Transaction> transactions) {
        return transactions.stream()
                .max(Comparator.comparingLong(dio.budgeting.domain.Transaction::getAmount)
                        .thenComparing(dio.budgeting.domain.Transaction::getOccurredOn, Comparator.reverseOrder())
                        .thenComparing(transaction -> transaction.getId().uuid(), Comparator.reverseOrder()))
                .map(TransactionOutput::from)
                .orElse(null);
    }

    // EnumMap iterates in Category declaration order and only a strictly greater total replaces
    // the current top, so ties are won by the category declared first.
    private static Category findTopCategory(EnumMap<Category, Long> totalsByCategoryInCents) {
        Category topCategory = null;
        long topTotal = 0L;
        for (var entry : totalsByCategoryInCents.entrySet()) {
            if (topCategory == null || entry.getValue() > topTotal) {
                topCategory = entry.getKey();
                topTotal = entry.getValue();
            }
        }
        return topCategory;
    }
}
