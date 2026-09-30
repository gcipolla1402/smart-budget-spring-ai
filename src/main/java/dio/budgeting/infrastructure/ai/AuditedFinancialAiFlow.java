package dio.budgeting.infrastructure.ai;

import dio.budgeting.application.InvalidInputException;
import dio.budgeting.infrastructure.audit.AiAuditError;
import dio.budgeting.infrastructure.audit.AiAuditService;
import dio.budgeting.infrastructure.audit.AiAuditStage;
import dio.budgeting.infrastructure.http.AiResponseFormat;
import dio.budgeting.infrastructure.http.AudioUploadTooLargeException;
import dio.budgeting.infrastructure.http.AudioUploadValidator;
import dio.budgeting.infrastructure.http.InvalidAudioUploadException;
import dio.budgeting.infrastructure.http.UnsupportedAudioFormatException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class AuditedFinancialAiFlow {
    private final AiAuditService auditService;
    private final AudioUploadValidator audioUploadValidator;
    private final FinancialAiService financialAiService;

    public AuditedFinancialAiFlow(AiAuditService auditService, AudioUploadValidator audioUploadValidator,
                                  FinancialAiService financialAiService) {
        this.auditService = auditService;
        this.audioUploadValidator = audioUploadValidator;
        this.financialAiService = financialAiService;
    }

    public AiFlowResult execute(MultipartFile file, String requestedFormat, String idempotencyKey) {
        var audit = auditService.start(requestedFormat, idempotencyKey != null && !idempotencyKey.isBlank());
        try {
            audit.stage(AiAuditStage.UPLOAD_VALIDATION);
            var format = AiResponseFormat.from(requestedFormat);
            audioUploadValidator.validate(file);

            var result = financialAiService.respondTo(file.getResource(), idempotencyKey, audit);
            byte[] audio = null;
            if (format == AiResponseFormat.AUDIO) {
                audio = financialAiService.synthesize(result.response(), audit);
            }
            audit.succeed(transactionId(result.transactionId()));
            return new AiFlowResult(format, result, audio, audit.id());
        }
        catch (InvalidAudioUploadException | UnsupportedAudioFormatException | AudioUploadTooLargeException
               | InvalidInputException exception) {
            audit.fail(AiAuditStage.UPLOAD_VALIDATION, AiAuditError.INVALID_UPLOAD);
            throw exception;
        }
        catch (AiServiceUnavailableException exception) {
            audit.fail(AiAuditStage.CHAT, AiAuditError.AI_UNAVAILABLE);
            throw exception;
        }
        catch (AiProviderException | TextToSpeechException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            audit.fail(AiAuditStage.RESPONSE, AiAuditError.UNEXPECTED);
            throw exception;
        }
    }

    private static UUID transactionId(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
