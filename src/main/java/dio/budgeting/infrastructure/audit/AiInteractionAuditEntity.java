package dio.budgeting.infrastructure.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction_audit")
public class AiInteractionAuditEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    @Column(nullable = false, length = 16)
    private String requestedFormat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiAuditStatus status;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AiAuditStage currentStage;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AiAuditStage failureStage;
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AiAuditError errorCode;
    @Column(nullable = false)
    private boolean idempotencyUsed;
    private Integer transcriptionLength;
    @Column(length = 64)
    private String transcriptionHash;
    private Integer responseLength;
    @Column(length = 64)
    private String responseHash;
    private UUID transactionId;

    protected AiInteractionAuditEntity() {
    }

    public AiInteractionAuditEntity(UUID id, Instant startedAt, String requestedFormat, boolean idempotencyUsed) {
        this.id = id;
        this.startedAt = startedAt;
        this.requestedFormat = requestedFormat;
        this.idempotencyUsed = idempotencyUsed;
        this.status = AiAuditStatus.STARTED;
        this.currentStage = AiAuditStage.UPLOAD_VALIDATION;
    }

    public UUID getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getRequestedFormat() { return requestedFormat; }
    public AiAuditStatus getStatus() { return status; }
    public AiAuditStage getCurrentStage() { return currentStage; }
    public AiAuditStage getFailureStage() { return failureStage; }
    public AiAuditError getErrorCode() { return errorCode; }
    public boolean isIdempotencyUsed() { return idempotencyUsed; }
    public Integer getTranscriptionLength() { return transcriptionLength; }
    public String getTranscriptionHash() { return transcriptionHash; }
    public Integer getResponseLength() { return responseLength; }
    public String getResponseHash() { return responseHash; }
    public UUID getTransactionId() { return transactionId; }

    public void setCurrentStage(AiAuditStage currentStage) { this.currentStage = currentStage; }
    public void setTranscriptionMetadata(int length, String hash) { transcriptionLength = length; transcriptionHash = hash; }
    public void setResponseMetadata(int length, String hash) { responseLength = length; responseHash = hash; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public void succeed(Instant finishedAt) {
        this.finishedAt = finishedAt;
        this.durationMs = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        this.status = AiAuditStatus.SUCCEEDED;
    }
    public void fail(Instant finishedAt, AiAuditStage stage, AiAuditError error) {
        this.finishedAt = finishedAt;
        this.durationMs = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        this.status = AiAuditStatus.FAILED;
        this.currentStage = stage;
        this.failureStage = stage;
        this.errorCode = error;
    }
}
