package dio.budgeting.infrastructure.audit;

import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface AiInteractionAuditRepository extends CrudRepository<AiInteractionAuditEntity, UUID> {
}
