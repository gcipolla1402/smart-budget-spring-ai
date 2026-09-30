package dio.budgeting.infrastructure.ai;

import dio.budgeting.infrastructure.http.AiResponseFormat;
import java.util.UUID;

public record AiFlowResult(AiResponseFormat format, FinancialAiResult result, byte[] audio, UUID auditId) {
}
