package dio.budgeting.infrastructure.http;

import dio.budgeting.application.CalculateCategoryTotalUseCase;
import dio.budgeting.application.GenerateFinancialSummaryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.CalculateCategoryTotalInput;
import dio.budgeting.application.input.GenerateFinancialSummaryInput;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.ai.FinancialAiService;
import dio.budgeting.infrastructure.ai.AuditedFinancialAiFlow;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.CategoryTotalResponse;
import dio.budgeting.infrastructure.http.response.FinancialSummaryResponse;
import dio.budgeting.infrastructure.http.response.FinancialAiResponse;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Cadastro, consultas financeiras e interação por áudio")
public class TransactionController {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final CalculateCategoryTotalUseCase calculateCategoryTotalUseCase;
    private final GenerateFinancialSummaryUseCase generateFinancialSummaryUseCase;

    private final AuditedFinancialAiFlow financialAiFlow;

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 CalculateCategoryTotalUseCase calculateCategoryTotalUseCase,
                                 GenerateFinancialSummaryUseCase generateFinancialSummaryUseCase,
                                 AuditedFinancialAiFlow financialAiFlow) {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.calculateCategoryTotalUseCase = calculateCategoryTotalUseCase;
        this.generateFinancialSummaryUseCase = generateFinancialSummaryUseCase;
        this.financialAiFlow = financialAiFlow;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar uma despesa",
            description = "Persiste uma despesa cujo valor é informado em centavos. Idempotency-Key é opcional: "
                    + "sem a chave, cada chamada cria uma transação; com ela, o mesmo payload recupera a transação "
                    + "original e um payload diferente gera conflito.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Despesa criada ou resultado idempotente recuperado",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":"550e8400-e29b-41d4-a716-446655440000","category":"GROCERIES",
                                     "description":"Compras do mês","amount":125.50,"occurredOn":"2026-09-30"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Payload ou chave de idempotência inválida",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chave reutilizada com conteúdo diferente",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Falha inesperada de persistência",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class)))
    })
    public TransactionResponse createTransaction(
            @Parameter(description = "Chave opcional que identifica a operação de criação",
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    description = "Despesa a cadastrar")
            @RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput(), idempotencyKey);
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    @Operation(summary = "Listar despesas por categoria",
            description = "Retorna todas as despesas persistidas na categoria informada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de despesas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Categoria inválida",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class)))
    })
    public List<TransactionResponse> readTransactions(
            @Parameter(description = "Categoria da despesa", example = "GROCERIES",
                    schema = @Schema(allowableValues = { "GROCERIES", "PHARMA", "AUTO" }))
            @PathVariable Category category) {
        return listTransactionsByCategoryUseCase.execute(category).stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/{category}/total")
    @Operation(summary = "Calcular o total por categoria",
            description = "Calcula em Java o total gasto e a quantidade de transações da categoria no período "
                    + "inclusivo entre start e end.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total calculado",
                    content = @Content(schema = @Schema(implementation = CategoryTotalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Categoria, data ou período inválido",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class)))
    })
    public CategoryTotalResponse readCategoryTotal(
                                                   @Parameter(description = "Categoria da despesa", example = "GROCERIES",
                                                           schema = @Schema(allowableValues = { "GROCERIES", "PHARMA", "AUTO" }))
                                                   @PathVariable Category category,
                                                   @Parameter(description = "Data inicial inclusiva", example = "2026-09-01")
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                   @Parameter(description = "Data final inclusiva", example = "2026-09-30")
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        var output = calculateCategoryTotalUseCase.execute(new CalculateCategoryTotalInput(category, start, end));
        return CategoryTotalResponse.from(output);
    }

    @GetMapping("/summary")
    @Operation(summary = "Consultar resumo financeiro",
            description = "Retorna total gasto, quantidade, distribuição por categoria, categoria com maior gasto e "
                    + "maior transação no período inclusivo. O domínio atual contém somente despesas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo calculado",
                    content = @Content(schema = @Schema(implementation = FinancialSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Data ausente, inválida ou período invertido",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class)))
    })
    public FinancialSummaryResponse readFinancialSummary(
                                                         @Parameter(description = "Data inicial inclusiva", example = "2026-09-01")
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                         @Parameter(description = "Data final inclusiva", example = "2026-09-30")
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        var output = generateFinancialSummaryUseCase.execute(new GenerateFinancialSummaryInput(start, end));
        return FinancialSummaryResponse.from(output);
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = { MediaType.APPLICATION_JSON_VALUE, "audio/mpeg" })
    @Operation(summary = "Processar comando financeiro por áudio",
            description = "Transcreve o áudio, interpreta a intenção com IA e executa as Tools da aplicação. "
                    + "responseFormat=text retorna JSON e não executa TTS; responseFormat=audio executa TTS uma vez "
                    + "e retorna MP3. Exige OPENAI_API_KEY; os demais endpoints REST funcionam sem OpenAI.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta textual JSON ou áudio MP3, conforme responseFormat",
                    headers = @Header(name = "X-AI-Execution-Id",
                            description = "Identificador da execução para correlação com a auditoria",
                            schema = @Schema(type = "string", format = "uuid")),
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = FinancialAiResponse.class),
                                    examples = @ExampleObject(value = """
                                            {"transcription":"Registre 80 reais de mercado",
                                             "response":"Despesa registrada com sucesso","responseFormat":"text",
                                             "transactionId":"550e8400-e29b-41d4-a716-446655440000"}
                                            """)),
                            @Content(mediaType = "audio/mpeg",
                                    schema = @Schema(type = "string", format = "binary"))
                    }),
            @ApiResponse(responseCode = "400", description = "Upload ausente/vazio, formato de resposta ou chave inválida",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chave reutilizada para operação diferente",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo maior que 25 MB",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Tipo de mídia ou extensão de áudio incompatível",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Falha do provedor de IA ou do TTS",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Integração de IA não configurada neste ambiente",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno inesperado",
                    content = @Content(schema = @Schema(implementation = dio.budgeting.infrastructure.http.response.ApiErrorResponse.class)))
    })
    ResponseEntity<?> transcribe(
            @Parameter(description = "Chave opcional reutilizada pela criação de transação via Tool",
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Formato final: text retorna JSON sem TTS; audio retorna audio/mpeg com TTS",
                    schema = @Schema(allowableValues = { "text", "audio" }, defaultValue = "text"))
            @RequestParam(name = "responseFormat", defaultValue = "text") String requestedFormat,
            @Parameter(description = "Áudio de até 25 MB. Extensões: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav ou webm",
                    required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestParam(name = "file", required = false) MultipartFile file) {
        var flow = financialAiFlow.execute(file, requestedFormat, idempotencyKey);
        var result = flow.result();

        if (flow.format() == AiResponseFormat.TEXT) {
            return ResponseEntity.ok()
                    .header("X-AI-Execution-Id", flow.auditId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(FinancialAiResponse.from(result));
        }

        var resource = new ByteArrayResource(flow.audio());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("X-AI-Execution-Id", flow.auditId().toString())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .body(resource);
    }
}
