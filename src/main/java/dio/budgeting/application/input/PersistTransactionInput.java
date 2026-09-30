package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

public record PersistTransactionInput(@ToolParam(description = "Descrição do gasto") String description,
                                      @ToolParam(description = "Valor do gasto (em centavos)") Long amount,
                                      @ToolParam(description = "Categoria de uma transação") Category category,
                                      @ToolParam(description = "Data em que o gasto ocorreu (yyyy-MM-dd). Omitir se não informada", required = false) LocalDate occurredOn) {
}
