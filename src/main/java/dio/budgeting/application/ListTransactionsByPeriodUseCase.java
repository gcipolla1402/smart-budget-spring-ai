package dio.budgeting.application;

import dio.budgeting.application.input.PeriodInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ListTransactionsByPeriodUseCase {
    private final TransactionRepository transactionRepository;

    public ListTransactionsByPeriodUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionOutput> execute(PeriodInput input) {
        if (input == null) throw new InvalidInputException("Period input must not be null");
        if (input.start() == null) throw new InvalidInputException("Start date must not be null");
        if (input.end() == null) throw new InvalidInputException("End date must not be null");
        if (input.start().isAfter(input.end())) throw new InvalidInputException("Start date must not be after end date");
        return transactionRepository.findAllByOccurredOnBetween(input.start(), input.end()).stream()
                .sorted(Comparator.comparing(dio.budgeting.domain.Transaction::getOccurredOn)
                        .thenComparing(transaction -> transaction.getId().uuid()))
                .map(TransactionOutput::from)
                .toList();
    }
}
