package dio.budgeting.infrastructure.persistence.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIntegrationTest {
    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void should_applyInitialMigrationToEmptyTestDatabase() {
        var applied = flyway.info().applied();

        assertThat(applied).singleElement().satisfies(migration -> {
            assertThat(migration.getVersion().getVersion()).isEqualTo("1");
            assertThat(migration.getDescription()).isEqualTo("create current schema");
        });
    }

    @Test
    void should_createExpectedTablesAndImportantConstraintsInMySql() {
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('transaction_entity', 'transaction_idempotency',
                                     'ai_interaction_audit', 'ai_tool_execution_audit')
                """)).isEqualTo(4);

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'transaction_entity'
                  AND column_name = 'description'
                  AND is_nullable = 'NO'
                  AND character_maximum_length = 255
                """)).isEqualTo(1);

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_transaction_amount_positive'
                  AND constraint_type = 'CHECK'
                """)).isEqualTo(1);

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'transaction_entity'
                  AND index_name IN ('idx_transaction_occurred_on', 'idx_transaction_category_occurred_on')
                """)).isEqualTo(3);

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name IN ('uk_transaction_idempotency_transaction',
                                          'fk_idempotency_transaction',
                                          'fk_ai_tool_interaction')
                """)).isEqualTo(3);
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
