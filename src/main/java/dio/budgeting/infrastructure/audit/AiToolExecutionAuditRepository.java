package dio.budgeting.infrastructure.audit;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AiToolExecutionAuditRepository extends CrudRepository<AiToolExecutionAuditEntity, UUID> {
    List<AiToolExecutionAuditEntity> findAllByInteractionId(UUID interactionId);
}
