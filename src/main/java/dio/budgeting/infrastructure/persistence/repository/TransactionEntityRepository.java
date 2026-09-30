package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionEntityRepository extends CrudRepository<TransactionEntity, UUID> {
    List<TransactionEntity> findAllByCategory(Category category);

    List<TransactionEntity> findAllByOccurredOnBetween(LocalDate start, LocalDate end);

    List<TransactionEntity> findAllByCategoryAndOccurredOnBetween(Category category, LocalDate start, LocalDate end);
}
