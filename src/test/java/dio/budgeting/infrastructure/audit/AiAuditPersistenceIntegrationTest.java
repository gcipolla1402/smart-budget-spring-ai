package dio.budgeting.infrastructure.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AiAuditPersistenceIntegrationTest {
    @Autowired
    AiAuditService auditService;
    @Autowired
    AiInteractionAuditRepository interactionRepository;
    @Autowired
    AiToolExecutionAuditRepository toolRepository;

    @Test
    void should_persistInteractionAndToolMetadataWithoutFullTexts() {
        var session = auditService.start("text", true);
        session.stage(AiAuditStage.TRANSCRIPTION);
        session.transcription("transcrição financeira privada");
        session.stage(AiAuditStage.CHAT);
        session.response("resposta financeira privada");
        var transactionId = UUID.randomUUID();
        session.toolSucceeded("persist-transaction", session.toolStarted(), transactionId);
        session.succeed(transactionId);

        var interaction = interactionRepository.findById(session.id()).orElseThrow();
        var tools = toolRepository.findAllByInteractionId(session.id());

        assertThat(interaction.getStatus()).isEqualTo(AiAuditStatus.SUCCEEDED);
        assertThat(interaction.isIdempotencyUsed()).isTrue();
        assertThat(interaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(interaction.getTranscriptionHash()).hasSize(64).doesNotContain("privada");
        assertThat(interaction.getResponseHash()).hasSize(64).doesNotContain("privada");
        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.getToolName()).isEqualTo("persist-transaction");
            assertThat(tool.getStatus()).isEqualTo(AiToolAuditStatus.SUCCEEDED);
            assertThat(tool.getTransactionId()).isEqualTo(transactionId);
        });
    }
}
