CREATE TABLE IF NOT EXISTS transaction_entity (
    id BINARY(16) NOT NULL,
    amount BIGINT NOT NULL,
    category ENUM('AUTO', 'GROCERIES', 'PHARMA') NOT NULL,
    description VARCHAR(255) NOT NULL,
    occurred_on DATE NOT NULL,
    CONSTRAINT pk_transaction_entity PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE transaction_entity
    MODIFY COLUMN description VARCHAR(255) NOT NULL,
    ADD CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0),
    ADD INDEX idx_transaction_occurred_on (occurred_on),
    ADD INDEX idx_transaction_category_occurred_on (category, occurred_on);

CREATE TABLE IF NOT EXISTS transaction_idempotency (
    idempotency_key VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    transaction_id BINARY(16) NULL,
    CONSTRAINT pk_transaction_idempotency PRIMARY KEY (idempotency_key),
    CONSTRAINT uk_transaction_idempotency_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_idempotency_transaction FOREIGN KEY (transaction_id)
        REFERENCES transaction_entity (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_interaction_audit (
    id BINARY(16) NOT NULL,
    current_stage ENUM('CHAT', 'PERSISTENCE', 'RESPONSE', 'TOOL', 'TRANSCRIPTION', 'TTS', 'UPLOAD_VALIDATION') NULL,
    duration_ms BIGINT NULL,
    error_code ENUM('AI_UNAVAILABLE', 'CHAT_PROVIDER', 'INVALID_UPLOAD', 'PERSISTENCE', 'TOOL_EXECUTION',
                    'TRANSCRIPTION_PROVIDER', 'TTS_PROVIDER', 'UNEXPECTED') NULL,
    failure_stage ENUM('CHAT', 'PERSISTENCE', 'RESPONSE', 'TOOL', 'TRANSCRIPTION', 'TTS', 'UPLOAD_VALIDATION') NULL,
    finished_at DATETIME(6) NULL,
    idempotency_used BIT(1) NOT NULL,
    requested_format VARCHAR(16) NOT NULL,
    response_hash VARCHAR(64) NULL,
    response_length INT NULL,
    started_at DATETIME(6) NOT NULL,
    status ENUM('FAILED', 'STARTED', 'SUCCEEDED') NOT NULL,
    transaction_id BINARY(16) NULL,
    transcription_hash VARCHAR(64) NULL,
    transcription_length INT NULL,
    CONSTRAINT pk_ai_interaction_audit PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ai_tool_execution_audit (
    id BINARY(16) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    interaction_id BINARY(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    status ENUM('FAILED', 'SUCCEEDED') NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    transaction_id BINARY(16) NULL,
    CONSTRAINT pk_ai_tool_execution_audit PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ai_tool_execution_audit
    ADD CONSTRAINT fk_ai_tool_interaction FOREIGN KEY (interaction_id)
        REFERENCES ai_interaction_audit (id);
