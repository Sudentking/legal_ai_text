-- 会话事实记忆（MySQL）
-- 建议在 mysql.url 指向的库中执行（默认 legal_dev）。

CREATE TABLE IF NOT EXISTS session_fact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    fact_key VARCHAR(100) NOT NULL,
    fact_value TEXT,
    source_type VARCHAR(20),
    source_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_fact (session_id, fact_key),
    INDEX idx_session_fact_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

