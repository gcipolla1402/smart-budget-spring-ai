package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionResponseTest {

    @Test
    void should_exposeAmountInReaisAndOccurredOn_when_mappingFromOutput() {
        var occurredOn = LocalDate.of(2026, 9, 15);
        var output = TransactionOutput.from(new Transaction("Mercado", 8000, Category.GROCERIES, occurredOn));

        var response = TransactionResponse.from(output);

        assertThat(response.amount()).isEqualTo(new BigDecimal("80.00"));
        assertThat(response.occurredOn()).isEqualTo(occurredOn);
        assertThat(response.id()).isEqualTo(output.id());
        assertThat(response.category()).isEqualTo("GROCERIES");
        assertThat(response.description()).isEqualTo("Mercado");
    }
}
