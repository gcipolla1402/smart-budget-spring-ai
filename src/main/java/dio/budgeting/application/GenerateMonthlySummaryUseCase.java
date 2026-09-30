package dio.budgeting.application;

import dio.budgeting.application.input.GenerateFinancialSummaryInput;
import dio.budgeting.application.input.MonthlySummaryInput;
import dio.budgeting.application.output.FinancialSummaryOutput;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.YearMonth;

@Service
public class GenerateMonthlySummaryUseCase {
    private final GenerateFinancialSummaryUseCase generateFinancialSummaryUseCase;

    public GenerateMonthlySummaryUseCase(GenerateFinancialSummaryUseCase generateFinancialSummaryUseCase) {
        this.generateFinancialSummaryUseCase = generateFinancialSummaryUseCase;
    }

    public FinancialSummaryOutput execute(MonthlySummaryInput input) {
        if (input == null) throw new InvalidInputException("Monthly summary input must not be null");
        if (input.year() == null) throw new InvalidInputException("Year must not be null");
        if (input.month() == null) throw new InvalidInputException("Month must not be null");
        try {
            var month = YearMonth.of(input.year(), input.month());
            return generateFinancialSummaryUseCase.execute(
                    new GenerateFinancialSummaryInput(month.atDay(1), month.atEndOfMonth()));
        }
        catch (DateTimeException exception) {
            throw new InvalidInputException("Year and month must define a valid month");
        }
    }
}
