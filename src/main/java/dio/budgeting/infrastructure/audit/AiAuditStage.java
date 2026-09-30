package dio.budgeting.infrastructure.audit;

public enum AiAuditStage {
    UPLOAD_VALIDATION,
    TRANSCRIPTION,
    CHAT,
    TOOL,
    PERSISTENCE,
    RESPONSE,
    TTS
}
