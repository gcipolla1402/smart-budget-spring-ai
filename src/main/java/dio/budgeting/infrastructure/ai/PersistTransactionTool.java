package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import org.springframework.ai.tool.annotation.Tool;

import java.util.concurrent.atomic.AtomicReference;

public class PersistTransactionTool {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final String idempotencyKey;
    private final AtomicReference<String> transactionId = new AtomicReference<>();

    public PersistTransactionTool(PersistTransactionUseCase persistTransactionUseCase, String idempotencyKey) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.idempotencyKey = idempotencyKey;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        var output = persistTransactionUseCase.execute(input, idempotencyKey);
        transactionId.compareAndSet(null, output.id());
        return output;
    }

    public String transactionId() {
        return transactionId.get();
    }
}
