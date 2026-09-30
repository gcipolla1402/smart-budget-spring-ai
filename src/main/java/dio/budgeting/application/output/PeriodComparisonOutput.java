package dio.budgeting.application.output;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record PeriodComparisonOutput(LocalDate firstStart, LocalDate firstEnd, BigDecimal firstTotal,
                                     LocalDate secondStart, LocalDate secondEnd, BigDecimal secondTotal,
                                     BigDecimal difference, BigDecimal percentageChange, String result) {
    public static PeriodComparisonOutput of(LocalDate firstStart, LocalDate firstEnd, long firstTotalInCents,
                                            LocalDate secondStart, LocalDate secondEnd, long secondTotalInCents) {
        var first = BigDecimal.valueOf(firstTotalInCents, 2);
        var second = BigDecimal.valueOf(secondTotalInCents, 2);
        var difference = second.subtract(first);
        var percentage = firstTotalInCents == 0
                ? null
                : difference.multiply(BigDecimal.valueOf(100)).divide(first, 2, RoundingMode.HALF_UP);
        var result = firstTotalInCents == secondTotalInCents ? "EQUAL"
                : firstTotalInCents > secondTotalInCents ? "FIRST_HIGHER" : "SECOND_HIGHER";
        return new PeriodComparisonOutput(firstStart, firstEnd, first, secondStart, secondEnd, second,
                difference, percentage, result);
    }
}
