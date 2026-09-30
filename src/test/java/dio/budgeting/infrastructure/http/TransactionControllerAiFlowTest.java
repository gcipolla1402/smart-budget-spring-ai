package dio.budgeting.infrastructure.http;

import dio.budgeting.application.CalculateCategoryTotalUseCase;
import dio.budgeting.application.GenerateFinancialSummaryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.ComparePeriodsUseCase;
import dio.budgeting.application.GenerateMonthlySummaryUseCase;
import dio.budgeting.application.GetExpensesByCategoryUseCase;
import dio.budgeting.application.ListTransactionsByPeriodUseCase;
import dio.budgeting.infrastructure.ai.FinancialQueryTools;
import dio.budgeting.infrastructure.ai.OpenAiFinancialAiService;
import dio.budgeting.infrastructure.ai.TextToSpeechException;
import dio.budgeting.infrastructure.http.response.FinancialAiResponse;
import dio.budgeting.support.InMemoryTransactionRepository;
import static dio.budgeting.support.AiTestSupport.auditedFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Real ChatClient on top of a fake ChatModel that only records the Prompt it receives.
// Uses the real system-message.st. No OpenAI call is made.
class TransactionControllerAiFlowTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final RecordingChatModel chatModel = new RecordingChatModel();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T15:00:00Z"), SAO_PAULO);
    private InMemoryTransactionRepository repository;
    private TransactionController controller;
    private TranscriptionModel transcriptionModel;
    private TextToSpeechModel textToSpeechModel;

    @BeforeEach
    void setUp() throws Exception {
        repository = new InMemoryTransactionRepository();

        transcriptionModel = mock(TranscriptionModel.class);
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("Quanto gastei com mercado este mês?");
        textToSpeechModel = mock(TextToSpeechModel.class);
        when(textToSpeechModel.call(anyString())).thenReturn(new byte[]{1, 2, 3});

        var persistTransactionUseCase = new PersistTransactionUseCase(repository, clock);
        var listTransactionsByCategoryUseCase = new ListTransactionsByCategoryUseCase(repository);
        var calculateCategoryTotalUseCase = new CalculateCategoryTotalUseCase(repository);
        var generateFinancialSummaryUseCase = new GenerateFinancialSummaryUseCase(repository);
        var financialQueryTools = new FinancialQueryTools(
                new GetExpensesByCategoryUseCase(repository),
                new GenerateMonthlySummaryUseCase(generateFinancialSummaryUseCase),
                new ComparePeriodsUseCase(repository),
                new ListTransactionsByPeriodUseCase(repository));
        var chatClient = ChatClient.builder(chatModel)
                .defaultSystem(new ClassPathResource("prompts/system-message.st")
                        .getContentAsString(StandardCharsets.UTF_8))
                .build();
        var financialAiService = new OpenAiFinancialAiService(
                transcriptionModel, chatClient, textToSpeechModel, clock, persistTransactionUseCase,
                listTransactionsByCategoryUseCase, calculateCategoryTotalUseCase,
                generateFinancialSummaryUseCase, financialQueryTools);

        controller = new TransactionController(
                persistTransactionUseCase,
                listTransactionsByCategoryUseCase,
                calculateCategoryTotalUseCase,
                generateFinancialSummaryUseCase,
                auditedFlow(financialAiService, new AudioUploadValidator(DataSize.ofMegabytes(25)), clock));
    }

    @Test
    void should_registerOriginalAndFinancialTools_when_callingTheModel() {
        controller.transcribe(null, "text", audio());

        var options = (ToolCallingChatOptions) chatModel.prompts.getFirst().getOptions();
        assertThat(options.getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "persist-transaction",
                        "list-transactions-by-category",
                        "calculate-category-total",
                        "generate-financial-summary",
                        "get-expenses-by-category",
                        "get-monthly-summary",
                        "compare-periods",
                        "list-expenses-by-period");

        var comparePeriods = options.getToolCallbacks().stream()
                .filter(tool -> tool.getToolDefinition().name().equals("compare-periods"))
                .findFirst().orElseThrow();
        assertThat(comparePeriods.call("""
                {"input":{"firstStart":"2026-08-01","firstEnd":"2026-08-31",
                 "secondStart":"2026-09-01","secondEnd":"2026-09-30"}}
                """)).contains("\"result\":\"EQUAL\"");
    }

    @Test
    void should_renderCurrentDateFromClock_when_callingTheModel() {
        controller.transcribe(null, "text", audio());

        var systemText = systemText(chatModel.prompts.getFirst());
        assertThat(systemText)
                .contains("Data atual: 2026-09-28")
                .doesNotContain("{currentDate}");
        assertThat(userText(chatModel.prompts.getFirst())).isEqualTo("Quanto gastei com mercado este mês?");
    }

    @Test
    void should_useClockZone_when_utcDateIsAlreadyTheNextDay() {
        // 01:00 UTC on Sep 29 is still 22:00 on Sep 28 in São Paulo
        clock.setInstant(Instant.parse("2026-09-29T01:00:00Z"));

        controller.transcribe(null, "text", audio());

        assertThat(systemText(chatModel.prompts.getFirst())).contains("Data atual: 2026-09-28");
    }

    @Test
    void should_notFreezeCurrentDate_when_clockAdvancesAfterControllerCreation() {
        controller.transcribe(null, "text", audio());
        clock.setInstant(Instant.parse("2026-09-29T15:00:00Z"));
        controller.transcribe(null, "text", audio());

        assertThat(systemText(chatModel.prompts.get(0))).contains("Data atual: 2026-09-28");
        assertThat(systemText(chatModel.prompts.get(1))).contains("Data atual: 2026-09-29");
    }

    @Test
    void should_returnTextWithTranscriptionAndModelAnswerByDefault() {
        var response = controller.transcribe(null, "text", audio());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(new FinancialAiResponse(
                "Quanto gastei com mercado este mês?", "Você gastou R$ 171,40.", "text", null));
        verify(textToSpeechModel, never()).call(anyString());
        assertThat(chatModel.prompts).hasSize(1);
    }

    @Test
    void should_synthesizeOnceAndKeepChatSingleCall_when_audioIsRequested() throws Exception {
        var response = controller.transcribe(null, "audio", audio());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/mpeg");
        assertThat(((Resource) response.getBody()).getContentAsByteArray()).containsExactly(1, 2, 3);
        verify(textToSpeechModel, times(1)).call("Você gastou R$ 171,40.");
        assertThat(chatModel.prompts).hasSize(1);
    }

    @Test
    void should_includeCreatedTransactionIdInTextResponse() {
        chatModel.persistTransactionOnCall = true;

        var response = controller.transcribe("voice-operation-id", "text", audio());
        var body = (FinancialAiResponse) response.getBody();

        assertThat(body.transactionId()).isNotBlank();
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_notRepeatPersistence_when_ttsFailsAndRequestIsRetried() {
        chatModel.persistTransactionOnCall = true;
        when(textToSpeechModel.call(anyString())).thenThrow(new RuntimeException("TTS unavailable"));

        assertThatThrownBy(() -> controller.transcribe("voice-operation-retry", "audio", audio()))
                .isInstanceOf(TextToSpeechException.class);
        assertThat(repository.saved).hasSize(1);

        assertThatThrownBy(() -> controller.transcribe("voice-operation-retry", "audio", audio()))
                .isInstanceOf(TextToSpeechException.class);
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_injectRequestIdempotencyKeyIntoPersistTool() {
        controller.transcribe("voice-operation-1", "text", audio());

        var options = (ToolCallingChatOptions) chatModel.prompts.getFirst().getOptions();
        var persistTool = options.getToolCallbacks().stream()
                .filter(tool -> tool.getToolDefinition().name().equals("persist-transaction"))
                .findFirst()
                .orElseThrow();
        var arguments = """
                {"input":{"description":"Mercado","amount":8000,"category":"GROCERIES"}}
                """;

        var first = persistTool.call(arguments);
        var repeated = persistTool.call(arguments);

        assertThat(repeated).isEqualTo(first);
        assertThat(repository.saved).hasSize(1);
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1});
    }

    private static String systemText(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(SystemMessage.class::isInstance)
                .findFirst()
                .orElseThrow()
                .getText();
    }

    private static String userText(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .findFirst()
                .orElseThrow()
                .getText();
    }

    static class RecordingChatModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        boolean persistTransactionOnCall;

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            if (persistTransactionOnCall) {
                var options = (ToolCallingChatOptions) prompt.getOptions();
                options.getToolCallbacks().stream()
                        .filter(tool -> tool.getToolDefinition().name().equals("persist-transaction"))
                        .findFirst()
                        .orElseThrow()
                        .call("""
                                {"input":{"description":"Mercado","amount":8000,"category":"GROCERIES"}}
                                """);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Você gastou R$ 171,40."))));
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
