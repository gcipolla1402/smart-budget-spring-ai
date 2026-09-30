package dio.budgeting.application;

import dio.budgeting.application.input.CalculateCategoryTotalInput;
import dio.budgeting.application.output.CategoryTotalOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class CalculateCategoryTotalUseCase {
    private final TransactionRepository transactionRepository;

    public CalculateCategoryTotalUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "calculate-category-total",
            description = "Calcula o total gasto em UMA categoria dentro de um período e a quantidade de transações. "
                    + "Use para perguntas como 'quanto gastei com mercado este mês'. Valores retornados em reais.")
    public CategoryTotalOutput execute(CalculateCategoryTotalInput input) {
        if (input == null) {
            throw new InvalidInputException("Category total input must not be null");
        }
        if (input.category() == null) {
            throw new InvalidInputException("Category must not be null");
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

        var transactions = transactionRepository.findAllByCategoryAndOccurredOnBetween(
                input.category(), input.start(), input.end());

        long totalInCents = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(0L, Math::addExact);

        return CategoryTotalOutput.of(input.category(), input.start(), input.end(), totalInCents, transactions.size());
    }
}
