-- AI LLM provider 运行时配置（单行表，应用层通过固定 id=1 的 upsert 语义写入）。
-- 与 V8 规范一致：utf8mb4 + 列级 CHECK；apiKey 对称加密后存入 api_key_cipher。
CREATE TABLE ai_llm_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    enabled         TINYINT(1)     NOT NULL DEFAULT 0,
    base_url        VARCHAR(512)   NOT NULL DEFAULT '',
    api_key_cipher  TEXT           NOT NULL,
    model           VARCHAR(128)   NOT NULL DEFAULT '',
    timeout_ms      INT            NOT NULL DEFAULT 8000,
    max_retries     INT            NOT NULL DEFAULT 1,
    updated_by      BIGINT         NULL,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_ai_llm_enabled  CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_ai_llm_timeout  CHECK (timeout_ms > 0),
    CONSTRAINT chk_ai_llm_retries  CHECK (max_retries >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
