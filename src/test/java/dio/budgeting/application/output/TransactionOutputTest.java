package dio.budgeting.application.output;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionOutputTest {
    private static final LocalDate OCCURRED_ON = LocalDate.of(2026, 9, 15);

    @ParameterizedTest
    @CsvSource({
            "8000, 80.00",
            "1, 0.01",
            "12345, 123.45",
            "0, 0.00",
    })
    void should_convertCentsToReais_when_mappingFromTransaction(long cents, String expectedReais) {
        var transaction = new Transaction("Mercado", cents, Category.GROCERIES, OCCURRED_ON);

        var output = TransactionOutput.from(transaction);

        assertThat(output.value()).isEqualTo(new BigDecimal(expectedReais));
    }

    @Test
    void should_keepOtherFields_when_mappingFromTransaction() {
        var transaction = new Transaction("Farmácia", 2590, Category.PHARMA, OCCURRED_ON);

        var output = TransactionOutput.from(transaction);

        assertThat(output.id()).isEqualTo(transaction.getId().uuid().toString());
        assertThat(output.description()).isEqualTo("Farmácia");
        assertThat(output.category()).isEqualTo("PHARMA");
        assertThat(output.occurredOn()).isEqualTo(OCCURRED_ON);
    }
}
