package dio.budgeting.support;

import dio.budgeting.infrastructure.ai.AuditedFinancialAiFlow;
import dio.budgeting.infrastructure.ai.FinancialAiService;
import dio.budgeting.infrastructure.audit.AiAuditService;
import dio.budgeting.infrastructure.audit.AiInteractionAuditEntity;
import dio.budgeting.infrastructure.audit.AiInteractionAuditRepository;
import dio.budgeting.infrastructure.audit.AiToolExecutionAuditRepository;
import dio.budgeting.infrastructure.http.AudioUploadValidator;

import java.time.Clock;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AiTestSupport {
    private AiTestSupport() {
    }

    public static AuditedFinancialAiFlow auditedFlow(FinancialAiService service,
                                                     AudioUploadValidator validator, Clock clock) {
        var stored = new HashMap<UUID, AiInteractionAuditEntity>();
        var interactions = mock(AiInteractionAuditRepository.class);
        var tools = mock(AiToolExecutionAuditRepository.class);
        when(interactions.save(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, AiInteractionAuditEntity.class);
            stored.put(entity.getId(), entity);
            return entity;
        });
        when(interactions.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(0, UUID.class))));
        when(tools.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new AuditedFinancialAiFlow(new AiAuditService(interactions, tools, clock), validator, service);
    }
}
