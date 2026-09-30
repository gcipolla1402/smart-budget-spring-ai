package dio.budgeting.infrastructure.ai;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.infrastructure.audit.AiAuditError;
import dio.budgeting.infrastructure.audit.AiAuditSession;
import dio.budgeting.infrastructure.audit.AiAuditStage;
import dio.budgeting.infrastructure.audit.AuditedToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.Clock;
import java.time.LocalDate;

public class OpenAiFinancialAiService implements FinancialAiService {
    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;
    private final Clock clock;
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final Object[] queryTools;

    public OpenAiFinancialAiService(TranscriptionModel transcriptionModel, ChatClient chatClient,
                                    TextToSpeechModel textToSpeechModel, Clock clock,
                                    PersistTransactionUseCase persistTransactionUseCase, Object... queryTools) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClient;
        this.textToSpeechModel = textToSpeechModel;
        this.clock = clock;
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.queryTools = queryTools.clone();
    }

    @Override
    public FinancialAiResult respondTo(Resource audio, String idempotencyKey, AiAuditSession audit) {
        final String userMessage;
        audit.stage(AiAuditStage.TRANSCRIPTION);
        try {
            userMessage = transcriptionModel.transcribe(audio);
            audit.transcription(userMessage);
        }
        catch (RuntimeException exception) {
            audit.fail(AiAuditStage.TRANSCRIPTION, AiAuditError.TRANSCRIPTION_PROVIDER);
            throw new AiProviderException(exception);
        }

        var persistTransactionTool = new PersistTransactionTool(persistTransactionUseCase, idempotencyKey);
        var toolObjects = new Object[queryTools.length + 1];
        System.arraycopy(queryTools, 0, toolObjects, 0, queryTools.length);
        toolObjects[queryTools.length] = persistTransactionTool;
        var callbacks = auditedCallbacks(ToolCallbacks.from(toolObjects), persistTransactionTool, audit);

        audit.stage(AiAuditStage.CHAT);
        try {
            var result = chatClient.prompt()
                    .system(system -> system.param("currentDate", LocalDate.now(clock).toString()))
                    .user(userMessage)
                    .toolCallbacks(callbacks)
                    .call()
                    .content();
            audit.response(result);
            return new FinancialAiResult(userMessage, result, persistTransactionTool.transactionId());
        }
        catch (RuntimeException exception) {
            var toolStage = audit.pendingToolFailureStage();
            audit.fail(toolStage == null ? AiAuditStage.CHAT : toolStage,
                    toolStage == null ? AiAuditError.CHAT_PROVIDER : audit.pendingToolError());
            throw new AiProviderException(exception);
        }
    }

    @Override
    public byte[] synthesize(String text, AiAuditSession audit) {
        audit.stage(AiAuditStage.TTS);
        if (textToSpeechModel == null) {
            audit.fail(AiAuditStage.TTS, AiAuditError.TTS_PROVIDER);
            throw new TextToSpeechException();
        }
        try {
            return textToSpeechModel.call(text);
        }
        catch (RuntimeException exception) {
            audit.fail(AiAuditStage.TTS, AiAuditError.TTS_PROVIDER);
            throw new TextToSpeechException(exception);
        }
    }

    private static ToolCallback[] auditedCallbacks(ToolCallback[] callbacks, PersistTransactionTool persistTool,
                                                   AiAuditSession audit) {
        var audited = new ToolCallback[callbacks.length];
        for (int index = 0; index < callbacks.length; index++) {
            var callback = callbacks[index];
            audited[index] = new AuditedToolCallback(callback, audit,
                    "persist-transaction".equals(callback.getToolDefinition().name())
                            ? persistTool::transactionId : null);
        }
        return audited;
    }
}
