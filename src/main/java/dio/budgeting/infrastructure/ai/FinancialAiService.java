package dio.budgeting.infrastructure.ai;

import org.springframework.core.io.Resource;
import dio.budgeting.infrastructure.audit.AiAuditSession;

public interface FinancialAiService {
    FinancialAiResult respondTo(Resource audio, String idempotencyKey, AiAuditSession audit);

    byte[] synthesize(String text, AiAuditSession audit);
}
