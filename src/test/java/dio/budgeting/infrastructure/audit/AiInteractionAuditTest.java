package dio.budgeting.infrastructure.audit;

import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.TransactionPersistenceException;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infrastructure.ai.AiProviderException;
import dio.budgeting.infrastructure.ai.AuditedFinancialAiFlow;
import dio.budgeting.infrastructure.ai.OpenAiFinancialAiService;
import dio.budgeting.infrastructure.ai.TextToSpeechException;
import dio.budgeting.infrastructure.http.AudioUploadValidator;
import dio.budgeting.infrastructure.http.InvalidAudioUploadException;
import dio.budgeting.support.InMemoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiInteractionAuditTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void should_auditSuccessfulTextWithoutPersistingSensitiveText() {
        var harness = new Harness(new InMemoryTransactionRepository(), false, false, false, null);

        var flow = harness.flow.execute(audio(), "text", null);
        var audit = harness.audit(flow.auditId());

        assertThat(audit.getStatus()).isEqualTo(AiAuditStatus.SUCCEEDED);
        assertThat(audit.getCurrentStage()).isEqualTo(AiAuditStage.RESPONSE);
        assertThat(audit.getRequestedFormat()).isEqualTo("text");
        assertThat(audit.getTranscriptionLength()).isEqualTo("conteúdo financeiro sensível".length());
        assertThat(audit.getResponseLength()).isEqualTo("resposta segura".length());
        assertThat(audit.getTranscriptionHash()).hasSize(64).doesNotContain("conteúdo financeiro sensível");
        assertThat(audit.getResponseHash()).hasSize(64).doesNotContain("resposta segura");
        assertThat(audit.getFinishedAt()).isNotNull();
        assertThat(audit.getDurationMs()).isZero();
    }

    @Test
    void should_auditAudioAndSuccessfulTts() {
        var harness = new Harness(new InMemoryTransactionRepository(), false, false, false, null);

        var flow = harness.flow.execute(audio(), "audio", null);

        assertThat(flow.audio()).containsExactly(1, 2, 3);
        var audit = harness.audit(flow.auditId());
        assertThat(audit.getStatus()).isEqualTo(AiAuditStatus.SUCCEEDED);
        assertThat(audit.getCurrentStage()).isEqualTo(AiAuditStage.TTS);
        assertThat(audit.getRequestedFormat()).isEqualTo("audio");
    }

    @Test
    void should_auditSuccessfulToolCreatedTransactionAndIdempotencyUsage() {
        var repository = new InMemoryTransactionRepository();
        var harness = new Harness(repository, false, false, true, null);

        var flow = harness.flow.execute(audio(), "text", "operation-1");
        var audit = harness.audit(flow.auditId());
        var tool = harness.tools.getFirst();

        assertThat(audit.isIdempotencyUsed()).isTrue();
        assertThat(audit.getTransactionId()).isNotNull();
        assertThat(audit.getTransactionId().toString()).isEqualTo(flow.result().transactionId());
        assertThat(tool.getToolName()).isEqualTo("persist-transaction");
        assertThat(tool.getStatus()).isEqualTo(AiToolAuditStatus.SUCCEEDED);
        assertThat(tool.getTransactionId()).isEqualTo(audit.getTransactionId());
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_auditTranscriptionFailureWithoutProviderMessage() {
        var harness = new Harness(new InMemoryTransactionRepository(), true, false, false, null);

        assertThatThrownBy(() -> harness.flow.execute(audio(), "text", null))
                .isInstanceOf(AiProviderException.class);

        var audit = harness.onlyAudit();
        assertThat(audit.getStatus()).isEqualTo(AiAuditStatus.FAILED);
        assertThat(audit.getCurrentStage()).isEqualTo(AiAuditStage.TRANSCRIPTION);
        assertThat(audit.getFailureStage()).isEqualTo(AiAuditStage.TRANSCRIPTION);
        assertThat(audit.getErrorCode()).isEqualTo(AiAuditError.TRANSCRIPTION_PROVIDER);
        assertThat(audit.getTranscriptionHash()).isNull();
    }

    @Test
    void should_auditChatFailure() {
        var harness = new Harness(new InMemoryTransactionRepository(), false, true, false, null);

        assertThatThrownBy(() -> harness.flow.execute(audio(), "text", null))
                .isInstanceOf(AiProviderException.class);

        assertThat(harness.onlyAudit().getCurrentStage()).isEqualTo(AiAuditStage.CHAT);
        assertThat(harness.onlyAudit().getFailureStage()).isEqualTo(AiAuditStage.CHAT);
        assertThat(harness.onlyAudit().getErrorCode()).isEqualTo(AiAuditError.CHAT_PROVIDER);
    }

    @Test
    void should_auditToolFailureSeparatelyFromChat() {
        var harness = new Harness(new InMemoryTransactionRepository(), false, false, false, new FailingTool());
        harness.chatModel.toolToCall = "failing-tool";

        assertThatThrownBy(() -> harness.flow.execute(audio(), "text", null))
                .isInstanceOf(AiProviderException.class);

        assertThat(harness.onlyAudit().getCurrentStage()).isEqualTo(AiAuditStage.TOOL);
        assertThat(harness.onlyAudit().getFailureStage()).isEqualTo(AiAuditStage.TOOL);
        assertThat(harness.onlyAudit().getErrorCode()).isEqualTo(AiAuditError.TOOL_EXECUTION);
        assertThat(harness.tools).singleElement().satisfies(tool -> {
            assertThat(tool.getToolName()).isEqualTo("failing-tool");
            assertThat(tool.getStatus()).isEqualTo(AiToolAuditStatus.FAILED);
        });
    }

    @Test
    void should_auditPersistenceFailureSeparately() {
        var harness = new Harness(new FailingPersistenceRepository(), false, false, true, null);

        assertThatThrownBy(() -> harness.flow.execute(audio(), "text", "operation-1"))
                .isInstanceOf(AiProviderException.class);

        assertThat(harness.onlyAudit().getCurrentStage()).isEqualTo(AiAuditStage.PERSISTENCE);
        assertThat(harness.onlyAudit().getFailureStage()).isEqualTo(AiAuditStage.PERSISTENCE);
        assertThat(harness.onlyAudit().getErrorCode()).isEqualTo(AiAuditError.PERSISTENCE);
        assertThat(harness.tools.getFirst().getStatus()).isEqualTo(AiToolAuditStatus.FAILED);
    }

    @Test
    void should_auditTtsFailureWithoutUndoingCreatedTransaction() {
        var repository = new InMemoryTransactionRepository();
        var harness = new Harness(repository, false, false, true, null);
        when(harness.textToSpeechModel.call(any(String.class))).thenThrow(new RuntimeException("provider secret"));

        assertThatThrownBy(() -> harness.flow.execute(audio(), "audio", "operation-tts"))
                .isInstanceOf(TextToSpeechException.class);

        assertThat(harness.onlyAudit().getStatus()).isEqualTo(AiAuditStatus.FAILED);
        assertThat(harness.onlyAudit().getCurrentStage()).isEqualTo(AiAuditStage.TTS);
        assertThat(harness.onlyAudit().getFailureStage()).isEqualTo(AiAuditStage.TTS);
        assertThat(harness.onlyAudit().getErrorCode()).isEqualTo(AiAuditError.TTS_PROVIDER);
        assertThat(harness.onlyAudit().getTransactionId()).isNotNull();
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_auditUploadValidationFailureBeforeCallingAi() {
        var harness = new Harness(new InMemoryTransactionRepository(), false, false, false, null);

        assertThatThrownBy(() -> harness.flow.execute(null, "text", null))
                .isInstanceOf(InvalidAudioUploadException.class);

        assertThat(harness.onlyAudit().getFailureStage()).isEqualTo(AiAuditStage.UPLOAD_VALIDATION);
        assertThat(harness.onlyAudit().getErrorCode()).isEqualTo(AiAuditError.INVALID_UPLOAD);
    }

    @Test
    void should_notBreakSuccessfulFlowWhenAuditStorageFails() {
        var interactions = mock(AiInteractionAuditRepository.class);
        var tools = mock(AiToolExecutionAuditRepository.class);
        when(interactions.save(any())).thenThrow(new RuntimeException("database unavailable"));
        var auditService = new AiAuditService(interactions, tools, CLOCK);
        var harness = new Harness(new InMemoryTransactionRepository(), false, false, false, null, auditService);

        var output = harness.flow.execute(audio(), "text", null);

        assertThat(output.result().response()).isEqualTo("resposta segura");
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1});
    }

    static class FailingTool {
        @Tool(name = "failing-tool", description = "Tool de teste que falha")
        public String execute() {
            throw new IllegalArgumentException("sensitive tool payload");
        }
    }

    static class Harness {
        final HashMap<UUID, AiInteractionAuditEntity> interactions = new HashMap<>();
        final List<AiToolExecutionAuditEntity> tools = new ArrayList<>();
        final RecordingChatModel chatModel = new RecordingChatModel();
        final TextToSpeechModel textToSpeechModel = mock(TextToSpeechModel.class);
        final AuditedFinancialAiFlow flow;

        Harness(TransactionRepository repository, boolean transcriptionFails, boolean chatFails,
                boolean persistTransaction, Object extraTool) {
            this(repository, transcriptionFails, chatFails, persistTransaction, extraTool, null);
        }

        Harness(TransactionRepository repository, boolean transcriptionFails, boolean chatFails,
                boolean persistTransaction, Object extraTool, AiAuditService providedAuditService) {
            var transcription = mock(TranscriptionModel.class);
            if (transcriptionFails) {
                when(transcription.transcribe(any())).thenThrow(new RuntimeException("provider secret"));
            }
            else {
                when(transcription.transcribe(any())).thenReturn("conteúdo financeiro sensível");
            }
            when(textToSpeechModel.call(any(String.class))).thenReturn(new byte[]{1, 2, 3});
            chatModel.fail = chatFails;
            chatModel.toolToCall = persistTransaction ? "persist-transaction" : null;
            var chatClient = ChatClient.builder(chatModel).defaultSystem("Data atual: {currentDate}").build();
            var service = new OpenAiFinancialAiService(transcription, chatClient, textToSpeechModel, CLOCK,
                    new PersistTransactionUseCase(repository, CLOCK), extraTool == null ? new Object[0] : new Object[]{extraTool});
            var auditService = providedAuditService == null ? auditService() : providedAuditService;
            flow = new AuditedFinancialAiFlow(auditService,
                    new AudioUploadValidator(DataSize.ofMegabytes(1)), service);
        }

        private AiAuditService auditService() {
            var interactionRepository = mock(AiInteractionAuditRepository.class);
            var toolRepository = mock(AiToolExecutionAuditRepository.class);
            when(interactionRepository.save(any())).thenAnswer(invocation -> {
                var entity = invocation.getArgument(0, AiInteractionAuditEntity.class);
                interactions.put(entity.getId(), entity);
                return entity;
            });
            when(interactionRepository.findById(any())).thenAnswer(invocation ->
                    Optional.ofNullable(interactions.get(invocation.getArgument(0, UUID.class))));
            when(toolRepository.save(any())).thenAnswer(invocation -> {
                var entity = invocation.getArgument(0, AiToolExecutionAuditEntity.class);
                tools.add(entity);
                return entity;
            });
            return new AiAuditService(interactionRepository, toolRepository, CLOCK);
        }

        AiInteractionAuditEntity audit(UUID id) { return interactions.get(id); }
        AiInteractionAuditEntity onlyAudit() { return interactions.values().iterator().next(); }
    }

    static class RecordingChatModel implements ChatModel {
        boolean fail;
        String toolToCall;

        @Override
        public ChatResponse call(Prompt prompt) {
            if (fail) throw new RuntimeException("provider secret");
            if (toolToCall != null) {
                var options = (ToolCallingChatOptions) prompt.getOptions();
                var callback = options.getToolCallbacks().stream()
                        .filter(tool -> tool.getToolDefinition().name().equals(toolToCall))
                        .findFirst().orElseThrow();
                var arguments = "persist-transaction".equals(toolToCall)
                        ? "{\"input\":{\"description\":\"Mercado\",\"amount\":8000,\"category\":\"GROCERIES\"}}"
                        : "{}";
                callback.call(arguments);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("resposta segura"))));
        }
    }

    static class FailingPersistenceRepository implements TransactionRepository {
        @Override public Transaction save(Transaction transaction) { throw new UnsupportedOperationException(); }
        @Override public Transaction saveIdempotently(Transaction transaction, String key, String fingerprint) {
            throw new TransactionPersistenceException(new RuntimeException("database secret"));
        }
        @Override public List<Transaction> findAllByCategory(Category category) { return List.of(); }
        @Override public List<Transaction> findAllByOccurredOnBetween(java.time.LocalDate start, java.time.LocalDate end) { return List.of(); }
        @Override public List<Transaction> findAllByCategoryAndOccurredOnBetween(Category category, java.time.LocalDate start, java.time.LocalDate end) { return List.of(); }
    }
}
