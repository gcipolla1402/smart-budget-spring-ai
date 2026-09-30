package dio.budgeting.application;

import dio.budgeting.application.input.PeriodInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ListTransactionsByPeriodUseCaseTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private final ListTransactionsByPeriodUseCase useCase = new ListTransactionsByPeriodUseCase(repository);

    @Test
    void should_includeBothPeriodBoundariesAndOrderByDate() {
        save("Fim", "2026-09-15");
        save("Antes", "2026-08-31");
        save("Início", "2026-09-01");
        save("Depois", "2026-09-16");

        var output = useCase.execute(new PeriodInput(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-15")));

        assertThat(output).extracting(transaction -> transaction.description()).containsExactly("Início", "Fim");
    }

    private void save(String description, String date) {
        repository.save(new Transaction(description, 100, Category.GROCERIES, LocalDate.parse(date)));
    }
}
