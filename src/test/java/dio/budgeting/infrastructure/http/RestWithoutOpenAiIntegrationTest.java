package dio.budgeting.infrastructure.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class RestWithoutOpenAiIntegrationTest {
    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    ApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void should_startWithoutOpenAiBeans_when_apiKeyIsAbsent() {
        assertThat(applicationContext.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(TranscriptionModel.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(TextToSpeechModel.class)).isEmpty();
    }

    @Test
    void should_createAndQueryTransactionsWithoutOpenAi() throws Exception {
        var occurredOn = LocalDate.of(2099, 7, 15);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Mercado sem IA","category":"GROCERIES","amount":12550,
                                 "occurredOn":"2099-07-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.occurredOn").value(occurredOn.toString()));

        mockMvc.perform(get("/transactions/GROCERIES"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mercado sem IA")));

        mockMvc.perform(get("/transactions/GROCERIES/total")
                        .param("start", "2099-07-01")
                        .param("end", "2099-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(125.50))
                .andExpect(jsonPath("$.transactionCount").value(1));

        mockMvc.perform(get("/transactions/summary")
                        .param("start", "2099-07-01")
                        .param("end", "2099-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(125.50))
                .andExpect(jsonPath("$.topCategory").value("GROCERIES"));
    }

    @Test
    void should_return503WithoutInternalDetails_when_aiEndpointIsCalledWithoutConfiguration() throws Exception {
        mockMvc.perform(multipart("/transactions/ai")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "audio.m4a", "audio/mp4", new byte[]{1, 2, 3})))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value(
                        "Serviço de Inteligência Artificial não configurado neste ambiente"))
                .andExpect(jsonPath("$.path").value("/transactions/ai"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("OpenAI"))));
    }
}
