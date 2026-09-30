package dio.budgeting.application.output;

import dio.budgeting.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionOutput(String id, String description, String category, BigDecimal value,
                                LocalDate occurredOn) {
    private static final int CENTS_SCALE = 2;

    public static TransactionOutput from(Transaction transaction) {
        return new TransactionOutput(
                transaction.getId().uuid().toString(),
                transaction.getDescription(),
                transaction.getCategory().name(),
                BigDecimal.valueOf(transaction.getAmount(), CENTS_SCALE),
                transaction.getOccurredOn());
    }
}
