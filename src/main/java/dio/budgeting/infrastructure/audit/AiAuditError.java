package dio.budgeting.infrastructure.audit;

public enum AiAuditError {
    INVALID_UPLOAD,
    TRANSCRIPTION_PROVIDER,
    CHAT_PROVIDER,
    TOOL_EXECUTION,
    PERSISTENCE,
    TTS_PROVIDER,
    AI_UNAVAILABLE,
    UNEXPECTED
}
