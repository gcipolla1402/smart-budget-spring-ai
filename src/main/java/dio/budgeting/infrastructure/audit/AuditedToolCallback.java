package dio.budgeting.infrastructure.audit;

import dio.budgeting.application.TransactionPersistenceException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.UUID;
import java.util.function.Supplier;

public class AuditedToolCallback implements ToolCallback {
    private final ToolCallback delegate;
    private final AiAuditSession audit;
    private final Supplier<String> transactionIdSupplier;

    public AuditedToolCallback(ToolCallback delegate, AiAuditSession audit, Supplier<String> transactionIdSupplier) {
        this.delegate = delegate;
        this.audit = audit;
        this.transactionIdSupplier = transactionIdSupplier;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String arguments) {
        return auditedCall(() -> delegate.call(arguments));
    }

    @Override
    public String call(String arguments, ToolContext toolContext) {
        return auditedCall(() -> delegate.call(arguments, toolContext));
    }

    private String auditedCall(Supplier<String> invocation) {
        var startedAt = audit.toolStarted();
        var toolName = delegate.getToolDefinition().name();
        try {
            var result = invocation.get();
            audit.toolSucceeded(toolName, startedAt, transactionId());
            return result;
        }
        catch (RuntimeException exception) {
            var persistenceFailure = hasCause(exception, TransactionPersistenceException.class);
            audit.toolFailed(toolName, startedAt,
                    persistenceFailure ? AiAuditStage.PERSISTENCE : AiAuditStage.TOOL,
                    persistenceFailure ? AiAuditError.PERSISTENCE : AiAuditError.TOOL_EXECUTION);
            throw exception;
        }
    }

    private UUID transactionId() {
        var value = transactionIdSupplier == null ? null : transactionIdSupplier.get();
        return value == null ? null : UUID.fromString(value);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }
}
