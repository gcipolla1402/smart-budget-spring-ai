package dio.budgeting.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AiAuditService {
    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);

    private final AiInteractionAuditRepository interactionRepository;
    private final AiToolExecutionAuditRepository toolRepository;
    private final Clock clock;

    public AiAuditService(AiInteractionAuditRepository interactionRepository,
                          AiToolExecutionAuditRepository toolRepository, Clock clock) {
        this.interactionRepository = interactionRepository;
        this.toolRepository = toolRepository;
        this.clock = clock;
    }

    public AiAuditSession start(String requestedFormat, boolean idempotencyUsed) {
        var id = UUID.randomUUID();
        var startedAt = clock.instant();
        var format = "audio".equalsIgnoreCase(requestedFormat) ? "audio"
                : "text".equalsIgnoreCase(requestedFormat) ? "text" : "invalid";
        var active = safeSave(new AiInteractionAuditEntity(id, startedAt, format, idempotencyUsed));
        log.atInfo().addKeyValue("aiExecutionId", id).addKeyValue("stage", AiAuditStage.UPLOAD_VALIDATION)
                .addKeyValue("status", "started").log("AI interaction started");
        return new AiAuditSession(id, startedAt, active, this);
    }

    Instant now() {
        return clock.instant();
    }

    void update(UUID id, Consumer<AiInteractionAuditEntity> change) {
        try {
            interactionRepository.findById(id).ifPresent(entity -> {
                change.accept(entity);
                interactionRepository.save(entity);
            });
        }
        catch (RuntimeException exception) {
            log.atWarn().addKeyValue("aiExecutionId", id).addKeyValue("status", "audit-write-failed")
                    .log("AI audit update could not be persisted");
        }
    }

    void saveTool(UUID interactionId, String toolName, Instant startedAt, AiToolAuditStatus status,
                  UUID transactionId) {
        try {
            toolRepository.save(new AiToolExecutionAuditEntity(
                    UUID.randomUUID(), interactionId, toolName, startedAt, now(), status, transactionId));
        }
        catch (RuntimeException exception) {
            log.atWarn().addKeyValue("aiExecutionId", interactionId).addKeyValue("tool", toolName)
                    .addKeyValue("status", "audit-write-failed").log("AI tool audit could not be persisted");
        }
    }

    String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean safeSave(AiInteractionAuditEntity entity) {
        try {
            interactionRepository.save(entity);
            return true;
        }
        catch (RuntimeException exception) {
            log.atWarn().addKeyValue("aiExecutionId", entity.getId()).addKeyValue("status", "audit-write-failed")
                    .log("AI audit could not be initialized");
            return false;
        }
    }
}
