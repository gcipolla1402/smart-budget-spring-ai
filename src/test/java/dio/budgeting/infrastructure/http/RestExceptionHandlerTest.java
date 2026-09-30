package dio.budgeting.infrastructure.http;

import dio.budgeting.application.CalculateCategoryTotalUseCase;
import dio.budgeting.application.GenerateFinancialSummaryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.infrastructure.ai.UnavailableFinancialAiService;
import dio.budgeting.support.InMemoryTransactionRepository;
import static dio.budgeting.support.AiTestSupport.auditedFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Standalone MockMvc: real controller, use cases and handler with an in-memory repository.
// No Spring context, Docker or OpenAI; the AI collaborators are mocks and are never called.
class RestExceptionHandlerTest {
    private final InMemoryTransactionRepository repository = new InMemoryTransactionRepository();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        var controller = new TransactionController(
                new PersistTransactionUseCase(repository, clock),
                new ListTransactionsByCategoryUseCase(repository),
                new CalculateCategoryTotalUseCase(repository),
                new GenerateFinancialSummaryUseCase(repository),
                auditedFlow(new UnavailableFinancialAiService(),
                        new AudioUploadValidator(DataSize.ofMegabytes(25)), clock));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void should_return400_when_summaryStartIsAfterEnd() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("start", "2026-09-30").param("end", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Start date must not be after end date"))
                .andExpect(jsonPath("$.path").value("/transactions/summary"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    void should_return400_when_categoryTotalStartIsAfterEnd() throws Exception {
        mockMvc.perform(get("/transactions/GROCERIES/total").param("start", "2026-09-30").param("end", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start date must not be after end date"))
                .andExpect(jsonPath("$.path").value("/transactions/GROCERIES/total"));
    }

    @Test
    void should_return400AndNotSave_when_amountIsZero() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero"));

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_return400AndNotSave_when_amountIsNegative() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": -100}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero"));

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_return400AndNotSave_when_descriptionIsBlank() throws Exception {
        postTransaction("""
                {"description": "   ", "category": "GROCERIES", "amount": 8000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description must not be blank"));

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_return400AndNotSave_when_descriptionIsEmpty() throws Exception {
        postTransaction("""
                {"description": "", "category": "GROCERIES", "amount": 8000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description must not be blank"));

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_return400AndNotSave_when_descriptionExceedsDatabaseLimit() throws Exception {
        var description = "x".repeat(256);

        postTransaction("""
                {"description": "%s", "category": "GROCERIES", "amount": 8000}
                """.formatted(description))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Description must not exceed 255 characters"));

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void should_return400_when_categoryIsMissingInBody() throws Exception {
        postTransaction("""
                {"description": "Mercado", "amount": 8000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category must not be null"));
    }

    @Test
    void should_return400_when_amountIsMissingOrNull() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must not be null"));

        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": null}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must not be null"));
    }

    @Test
    void should_return400_when_amountHasInvalidType() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": "invalid"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void should_return400WithAcceptedValues_when_categoryInPathIsInvalid() throws Exception {
        mockMvc.perform(get("/transactions/FOO/total").param("start", "2026-09-01").param("end", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid value 'FOO' for parameter 'category'. Accepted values: GROCERIES, PHARMA, AUTO"));

        mockMvc.perform(get("/transactions/FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/transactions/FOO"));
    }

    @Test
    void should_return400_when_dateHasInvalidFormat() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("start", "abc").param("end", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'start'"));
    }

    @Test
    void should_return400_when_requiredParameterIsMissing() throws Exception {
        mockMvc.perform(get("/transactions/summary").param("start", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'end' is missing"));
    }

    @Test
    void should_return400WithoutInternalDetails_when_jsonIsMalformed() throws Exception {
        postTransaction("{\"description\": \"Mercado\", ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void should_return400_when_bodyHasInvalidCategoryOrDate() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "FOO", "amount": 8000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": 8000, "occurredOn": "15/09/2026"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void should_return201_when_transactionIsValid() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": 8000}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(80.0))
                .andExpect(jsonPath("$.occurredOn").value("2026-09-28"));

        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_returnOriginalTransactionAndNotDuplicate_when_requestIsRepeatedWithSameKey() throws Exception {
        var json = """
                {"description": "Mercado", "category": "GROCERIES", "amount": 8000}
                """;

        var firstResponse = postTransaction(json, "rest-operation-1")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var repeatedResponse = postTransaction(json, "rest-operation-1")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_return409_when_sameKeyIsReusedWithDifferentPayload() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": 8000}
                """, "rest-operation-1").andExpect(status().isCreated());

        postTransaction("""
                {"description": "Farmácia", "category": "PHARMA", "amount": 4590}
                """, "rest-operation-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Idempotency key has already been used with different transaction data"));

        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void should_return400_when_idempotencyKeyIsInvalid() throws Exception {
        postTransaction("""
                {"description": "Mercado", "category": "GROCERIES", "amount": 8000}
                """, "invalid key")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key contains invalid characters"));

        assertThat(repository.saved).isEmpty();
    }

    private ResultActions postTransaction(String json) throws Exception {
        return mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions postTransaction(String json, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/transactions")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
