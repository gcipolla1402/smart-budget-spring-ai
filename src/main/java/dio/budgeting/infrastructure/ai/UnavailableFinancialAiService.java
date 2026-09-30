package dio.budgeting.infrastructure.ai;

import org.springframework.core.io.Resource;
import dio.budgeting.infrastructure.audit.AiAuditSession;

public class UnavailableFinancialAiService implements FinancialAiService {
    @Override
    public FinancialAiResult respondTo(Resource audio, String idempotencyKey, AiAuditSession audit) {
        throw new AiServiceUnavailableException();
    }

    @Override
    public byte[] synthesize(String text, AiAuditSession audit) {
        throw new AiServiceUnavailableException();
    }
}
