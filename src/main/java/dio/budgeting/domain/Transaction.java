package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class Transaction {
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    private TransactionId id;
    private String description;
    private long amount;
    private Category category;
    private LocalDate occurredOn;

    public Transaction(String description, long amount, Category category, LocalDate occurredOn) {
        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.occurredOn = occurredOn;
    }
}
