-- Agent 任务历史 + 问答日志（MySQL）
-- 建议在 mysql.url 指向的库中执行（默认 legal_dev）。

CREATE TABLE IF NOT EXISTS qa_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT,
    agent_state VARCHAR(50),
    status VARCHAR(20) NOT NULL, -- PENDING / SUCCESS / FAIL
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_qa_log_session (session_id),
    INDEX idx_qa_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_task_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_query TEXT NOT NULL,
    intent_type VARCHAR(50),
    strategy_used VARCHAR(50),   -- SQL / VECTOR / FACT_CHECK / TOOL / HYBRID
    result_status VARCHAR(20),   -- PENDING / SUCCESS / FAIL
    fail_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_task_session (session_id),
    INDEX idx_agent_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
