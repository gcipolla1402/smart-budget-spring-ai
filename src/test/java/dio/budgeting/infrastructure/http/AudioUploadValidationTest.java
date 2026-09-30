package dio.budgeting.infrastructure.http;

import dio.budgeting.application.CalculateCategoryTotalUseCase;
import dio.budgeting.application.GenerateFinancialSummaryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.infrastructure.ai.AiProviderException;
import dio.budgeting.infrastructure.ai.FinancialAiService;
import dio.budgeting.infrastructure.ai.FinancialAiResult;
import dio.budgeting.infrastructure.ai.TextToSpeechException;
import dio.budgeting.infrastructure.audit.AiAuditSession;
import dio.budgeting.support.InMemoryTransactionRepository;
import static dio.budgeting.support.AiTestSupport.auditedFlow;
import static org.mockito.ArgumentMatchers.eq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import java.time.Clock;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AudioUploadValidationTest {
    private FinancialAiService financialAiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var repository = new InMemoryTransactionRepository();
        var clock = Clock.systemUTC();
        financialAiService = mock(FinancialAiService.class);
        var controller = new TransactionController(
                new PersistTransactionUseCase(repository, clock),
                new ListTransactionsByCategoryUseCase(repository),
                new CalculateCategoryTotalUseCase(repository),
                new GenerateFinancialSummaryUseCase(repository),
                auditedFlow(financialAiService, new AudioUploadValidator(DataSize.ofBytes(4)), clock));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void should_return400AndNotCallAi_when_audioPartIsMissing() throws Exception {
        mockMvc.perform(multipart("/transactions/ai"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required multipart file 'file' is missing"));

        verifyNoInteractions(financialAiService);
    }

    @Test
    void should_return400AndNotCallAi_when_audioIsEmpty() throws Exception {
        perform(new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Audio file must not be empty"));

        verifyNoInteractions(financialAiService);
    }

    @Test
    void should_return413AndNotCallAi_when_audioExceedsConfiguredLimit() throws Exception {
        perform(new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[5]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Audio file exceeds the maximum allowed size of 4 bytes"));

        verifyNoInteractions(financialAiService);
    }

    @Test
    void should_return415AndNotCallAi_when_extensionIsUnsupported() throws Exception {
        perform(new MockMultipartFile("file", "audio.txt", "audio/mpeg", new byte[]{1}))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported audio file extension"));

        verifyNoInteractions(financialAiService);
    }

    @Test
    void should_return415AndNotCallAi_when_mediaTypeIsUnsupported() throws Exception {
        perform(new MockMultipartFile("file", "audio.mp3", "text/plain", new byte[]{1}))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported audio media type"));

        verifyNoInteractions(financialAiService);
    }

    @Test
    void should_return502WithoutProviderDetails_when_aiProviderFails() throws Exception {
        doThrow(new AiProviderException(new RuntimeException("provider secret")))
                .when(financialAiService).respondTo(any(), isNull(), any(AiAuditSession.class));

        perform(new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1}))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Artificial Intelligence service failed to process the request"))
                .andExpect(content().string(not(containsString("provider secret"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void should_return502WithoutProviderDetails_when_textToSpeechFails() throws Exception {
        when(financialAiService.respondTo(any(), isNull(), any(AiAuditSession.class)))
                .thenReturn(new FinancialAiResult("transcription", "answer", null));
        doThrow(new TextToSpeechException(new RuntimeException("provider secret")))
                .when(financialAiService).synthesize(eq("answer"), any(AiAuditSession.class));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1}))
                        .param("responseFormat", "audio"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Text-to-Speech service failed to generate the audio response"))
                .andExpect(content().string(not(containsString("provider secret"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void should_returnJsonByDefaultAndNotCallTextToSpeech() throws Exception {
        when(financialAiService.respondTo(any(), isNull(), any(AiAuditSession.class)))
                .thenReturn(new FinancialAiResult("Comprar café", "Transação registrada",
                        "550e8400-e29b-41d4-a716-446655440000"));

        perform(new MockMultipartFile("file", "audio.m4a", "audio/mp4", new byte[]{1}))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.transcription").value("Comprar café"))
                .andExpect(jsonPath("$.response").value("Transação registrada"))
                .andExpect(jsonPath("$.responseFormat").value("text"))
                .andExpect(jsonPath("$.transactionId").value("550e8400-e29b-41d4-a716-446655440000"));

        verify(financialAiService, never()).synthesize(any(), any());
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/transactions/ai").file(file));
    }
}
