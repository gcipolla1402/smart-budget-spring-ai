package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.FinancialSummaryOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class FinancialSummaryResponseTest {

    @Test
    void should_copyAllFields_when_mappingFromOutput() {
        var start = LocalDate.of(2026, 9, 1);
        var end = LocalDate.of(2026, 9, 30);
        var totalsInCents = new EnumMap<Category, Long>(Category.class);
        totalsInCents.put(Category.GROCERIES, 12550L);
        totalsInCents.put(Category.PHARMA, 4590L);
        var output = FinancialSummaryOutput.of(start, end, 17140, 2, totalsInCents, Category.GROCERIES);

        var response = FinancialSummaryResponse.from(output);

        assertThat(response.start()).isEqualTo(start);
        assertThat(response.end()).isEqualTo(end);
        assertThat(response.total()).isEqualTo(new BigDecimal("171.40"));
        assertThat(response.transactionCount()).isEqualTo(2);
        assertThat(response.totalsByCategory()).containsExactly(
                entry("GROCERIES", new BigDecimal("125.50")),
                entry("PHARMA", new BigDecimal("45.90")));
        assertThat(response.topCategory()).isEqualTo("GROCERIES");
        assertThat(response.largestTransaction()).isNull();
    }

    @Test
    void should_keepNullTopCategory_when_outputHasNoTransactions() {
        var day = LocalDate.of(2026, 9, 1);
        var output = FinancialSummaryOutput.of(day, day, 0, 0, new EnumMap<>(Category.class), null);

        var response = FinancialSummaryResponse.from(output);

        assertThat(response.topCategory()).isNull();
        assertThat(response.totalsByCategory()).isEmpty();
        assertThat(response.total()).isEqualTo(new BigDecimal("0.00"));
    }
}
