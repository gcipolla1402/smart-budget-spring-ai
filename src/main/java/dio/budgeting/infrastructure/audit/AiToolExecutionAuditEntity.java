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
@Table(name = "ai_tool_execution_audit")
public class AiToolExecutionAuditEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID interactionId;
    @Column(nullable = false, length = 100)
    private String toolName;
    @Column(nullable = false)
    private Instant startedAt;
    @Column(nullable = false)
    private Instant finishedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiToolAuditStatus status;
    private UUID transactionId;

    protected AiToolExecutionAuditEntity() {
    }

    public AiToolExecutionAuditEntity(UUID id, UUID interactionId, String toolName, Instant startedAt,
                                      Instant finishedAt, AiToolAuditStatus status, UUID transactionId) {
        this.id = id;
        this.interactionId = interactionId;
        this.toolName = toolName;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.transactionId = transactionId;
    }

    public UUID getId() { return id; }
    public UUID getInteractionId() { return interactionId; }
    public String getToolName() { return toolName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public AiToolAuditStatus getStatus() { return status; }
    public UUID getTransactionId() { return transactionId; }
}
