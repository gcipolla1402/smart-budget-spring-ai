package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against the MySQL from compose.yml; each test is rolled back.
// Dates are kept in 2000 so existing development data does not interfere.
@SpringBootTest
@Transactional
class JpaTransactionRepositoryIntegrationTest {
    private static final LocalDate START = LocalDate.of(2000, 1, 10);
    private static final LocalDate END = LocalDate.of(2000, 1, 20);

    @Autowired
    TransactionRepository transactionRepository;

    @Test
    void should_includeBoundariesAndExcludeOutside_when_findingByPeriod() {
        var before = save(Category.GROCERIES, START.minusDays(1));
        var onStart = save(Category.GROCERIES, START);
        var middle = save(Category.PHARMA, LocalDate.of(2000, 1, 15));
        var onEnd = save(Category.AUTO, END);
        var after = save(Category.GROCERIES, END.plusDays(1));

        var result = transactionRepository.findAllByOccurredOnBetween(START, END);

        assertThat(result).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(onStart.getId(), middle.getId(), onEnd.getId())
                .doesNotContain(before.getId(), after.getId());
    }

    @Test
    void should_filterByCategoryAndPeriod_when_findingByCategoryAndPeriod() {
        var groceriesBefore = save(Category.GROCERIES, START.minusDays(1));
        var groceriesOnStart = save(Category.GROCERIES, START);
        var groceriesOnEnd = save(Category.GROCERIES, END);
        var groceriesAfter = save(Category.GROCERIES, END.plusDays(1));
        var pharmaInPeriod = save(Category.PHARMA, LocalDate.of(2000, 1, 15));

        var result = transactionRepository.findAllByCategoryAndOccurredOnBetween(Category.GROCERIES, START, END);

        assertThat(result).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(groceriesOnStart.getId(), groceriesOnEnd.getId())
                .doesNotContain(groceriesBefore.getId(), groceriesAfter.getId(), pharmaInPeriod.getId());
        assertThat(result).extracting(Transaction::getCategory).containsOnly(Category.GROCERIES);
    }

    @Test
    void should_returnDomainTransactionsWithAllFields_when_findingByPeriod() {
        var saved = transactionRepository.save(
                new Transaction("Mercado", 12550, Category.GROCERIES, START));

        var result = transactionRepository.findAllByOccurredOnBetween(START, START);

        assertThat(result).singleElement().satisfies(transaction -> {
            assertThat(transaction.getId()).isEqualTo(saved.getId());
            assertThat(transaction.getDescription()).isEqualTo("Mercado");
            assertThat(transaction.getAmount()).isEqualTo(12550);
            assertThat(transaction.getCategory()).isEqualTo(Category.GROCERIES);
            assertThat(transaction.getOccurredOn()).isEqualTo(START);
        });
    }

    private Transaction save(Category category, LocalDate occurredOn) {
        return transactionRepository.save(new Transaction("Teste " + occurredOn, 1000, category, occurredOn));
    }
}
