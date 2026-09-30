package dio.budgeting.application;

import dio.budgeting.application.input.PeriodInput;
import dio.budgeting.application.output.CategoryExpenseOutput;
import dio.budgeting.application.output.ExpensesByCategoryOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;

@Service
public class GetExpensesByCategoryUseCase {
    private final TransactionRepository transactionRepository;

    public GetExpensesByCategoryUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public ExpensesByCategoryOutput execute(PeriodInput input) {
        validate(input);
        var totals = new EnumMap<Category, Long>(Category.class);
        var counts = new EnumMap<Category, Integer>(Category.class);
        Arrays.stream(Category.values()).forEach(category -> {
            totals.put(category, 0L);
            counts.put(category, 0);
        });

        long total = 0L;
        for (var transaction : transactionRepository.findAllByOccurredOnBetween(input.start(), input.end())) {
            total = Math.addExact(total, transaction.getAmount());
            totals.merge(transaction.getCategory(), transaction.getAmount(), Math::addExact);
            counts.merge(transaction.getCategory(), 1, Math::addExact);
        }

        var categories = Arrays.stream(Category.values())
                .sorted(Comparator.<Category>comparingLong(totals::get).reversed()
                        .thenComparingInt(Enum::ordinal))
                .map(category -> CategoryExpenseOutput.of(category, totals.get(category), counts.get(category)))
                .toList();
        return ExpensesByCategoryOutput.of(input.start(), input.end(), total, categories);
    }

    private static void validate(PeriodInput input) {
        if (input == null) throw new InvalidInputException("Period input must not be null");
        if (input.start() == null) throw new InvalidInputException("Start date must not be null");
        if (input.end() == null) throw new InvalidInputException("End date must not be null");
        if (input.start().isAfter(input.end())) throw new InvalidInputException("Start date must not be after end date");
    }
}
