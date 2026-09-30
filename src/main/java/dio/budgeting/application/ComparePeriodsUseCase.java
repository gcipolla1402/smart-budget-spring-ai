package dio.budgeting.application;

import dio.budgeting.application.input.ComparePeriodsInput;
import dio.budgeting.application.output.PeriodComparisonOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ComparePeriodsUseCase {
    private final TransactionRepository transactionRepository;

    public ComparePeriodsUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public PeriodComparisonOutput execute(ComparePeriodsInput input) {
        if (input == null) throw new InvalidInputException("Period comparison input must not be null");
        validatePeriod(input.firstStart(), input.firstEnd(), "First");
        validatePeriod(input.secondStart(), input.secondEnd(), "Second");
        var firstTotal = total(input.firstStart(), input.firstEnd());
        var secondTotal = total(input.secondStart(), input.secondEnd());
        return PeriodComparisonOutput.of(input.firstStart(), input.firstEnd(), firstTotal,
                input.secondStart(), input.secondEnd(), secondTotal);
    }

    private long total(LocalDate start, LocalDate end) {
        return transactionRepository.findAllByOccurredOnBetween(start, end).stream()
                .map(Transaction::getAmount).reduce(0L, Math::addExact);
    }

    private static void validatePeriod(LocalDate start, LocalDate end, String label) {
        if (start == null) throw new InvalidInputException(label + " start date must not be null");
        if (end == null) throw new InvalidInputException(label + " end date must not be null");
        if (start.isAfter(end)) throw new InvalidInputException(label + " start date must not be after end date");
    }
}
