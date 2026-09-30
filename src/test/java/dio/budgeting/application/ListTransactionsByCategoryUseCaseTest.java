package dio.budgeting.application;

import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListTransactionsByCategoryUseCaseTest {
    private final ListTransactionsByCategoryUseCase useCase =
            new ListTransactionsByCategoryUseCase(new InMemoryTransactionRepository());

    @Test
    void should_throwInvalidInput_when_categoryIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Category must not be null");
    }
}
