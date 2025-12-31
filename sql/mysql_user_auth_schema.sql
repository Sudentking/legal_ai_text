-- 用户注册/登录/权限体系（MySQL）
-- 建议在 mysql.url 指向的库中执行（默认 legal_dev）。

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,          -- USER / SUPER_ADMIN
    status VARCHAR(16) NOT NULL,        -- ACTIVE / DISABLED
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_perm (user_id, permission_code),
    INDEX idx_user_perm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,        -- ACTIVE / REVOKED
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

