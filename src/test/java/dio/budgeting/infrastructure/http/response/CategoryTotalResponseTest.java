package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.CategoryTotalOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTotalResponseTest {

    @Test
    void should_copyAllFields_when_mappingFromOutput() {
        var start = LocalDate.of(2026, 9, 1);
        var end = LocalDate.of(2026, 9, 30);
        var output = CategoryTotalOutput.of(Category.GROCERIES, start, end, 12550, 1);

        var response = CategoryTotalResponse.from(output);

        assertThat(response.category()).isEqualTo("GROCERIES");
        assertThat(response.start()).isEqualTo(start);
        assertThat(response.end()).isEqualTo(end);
        assertThat(response.total()).isEqualTo(new BigDecimal("125.50"));
        assertThat(response.transactionCount()).isEqualTo(1);
    }
}
