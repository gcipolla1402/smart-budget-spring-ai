package dio.budgeting.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

public final class AiAuditSession {
    private static final Logger log = LoggerFactory.getLogger(AiAuditSession.class);

    private final UUID id;
    private final Instant startedAt;
    private final boolean active;
    private final AiAuditService service;
    private AiAuditStage pendingToolFailureStage;
    private AiAuditError pendingToolError;
    private boolean finished;

    AiAuditSession(UUID id, Instant startedAt, boolean active, AiAuditService service) {
        this.id = id;
        this.startedAt = startedAt;
        this.active = active;
        this.service = service;
    }

    public UUID id() { return id; }
    public Instant startedAt() { return startedAt; }

    public synchronized void stage(AiAuditStage stage) {
        if (!finished && active) service.update(id, entity -> entity.setCurrentStage(stage));
        log.atInfo().addKeyValue("aiExecutionId", id).addKeyValue("stage", stage)
                .addKeyValue("status", "started").log("AI stage started");
    }

    public synchronized void transcription(String text) {
        if (active) service.update(id, entity -> entity.setTranscriptionMetadata(text.length(), service.hash(text)));
    }

    public synchronized void response(String text) {
        if (active) service.update(id, entity -> {
            entity.setCurrentStage(AiAuditStage.RESPONSE);
            entity.setResponseMetadata(text.length(), service.hash(text));
        });
    }

    public Instant toolStarted() {
        return service.now();
    }

    public synchronized void toolSucceeded(String toolName, Instant toolStartedAt, UUID transactionId) {
        if (active) {
            service.saveTool(id, toolName, toolStartedAt, AiToolAuditStatus.SUCCEEDED, transactionId);
            if (transactionId != null) service.update(id, entity -> entity.setTransactionId(transactionId));
        }
        log.atInfo().addKeyValue("aiExecutionId", id).addKeyValue("stage", AiAuditStage.TOOL)
                .addKeyValue("tool", toolName).addKeyValue("status", "succeeded").log("AI tool completed");
    }

    public synchronized void toolFailed(String toolName, Instant toolStartedAt, AiAuditStage stage,
                                        AiAuditError error) {
        if (active) service.saveTool(id, toolName, toolStartedAt, AiToolAuditStatus.FAILED, null);
        pendingToolFailureStage = stage;
        pendingToolError = error;
        log.atWarn().addKeyValue("aiExecutionId", id).addKeyValue("stage", stage)
                .addKeyValue("tool", toolName).addKeyValue("status", "failed").log("AI tool failed");
    }

    public synchronized AiAuditStage pendingToolFailureStage() { return pendingToolFailureStage; }
    public synchronized AiAuditError pendingToolError() { return pendingToolError; }

    public synchronized void succeed(UUID transactionId) {
        if (finished) return;
        finished = true;
        var finishedAt = service.now();
        if (active) service.update(id, entity -> {
            if (transactionId != null) entity.setTransactionId(transactionId);
            entity.succeed(finishedAt);
        });
        log.atInfo().addKeyValue("aiExecutionId", id).addKeyValue("status", "succeeded")
                .addKeyValue("durationMs", Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli()))
                .log("AI interaction completed");
    }

    public synchronized void fail(AiAuditStage stage, AiAuditError error) {
        if (finished) return;
        finished = true;
        var finishedAt = service.now();
        if (active) service.update(id, entity -> entity.fail(finishedAt, stage, error));
        var event = error == AiAuditError.UNEXPECTED || error == AiAuditError.PERSISTENCE
                ? log.atError() : log.atWarn();
        event.addKeyValue("aiExecutionId", id).addKeyValue("stage", stage)
                .addKeyValue("status", "failed").addKeyValue("errorCode", error)
                .log("AI interaction failed");
    }
}
