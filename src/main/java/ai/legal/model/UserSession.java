package ai.legal.model;

import java.sql.Timestamp;

/**
 * 登录会话实体（MySQL: user_session）。
 */
public class UserSession {
    private Long id;
    private String sessionId;
    private Long userId;
    private UserSessionStatus status;
    private Timestamp createdAt;
    private Timestamp expiresAt;

    public UserSession() {
    }

    public UserSession(String sessionId, Long userId, UserSessionStatus status, Timestamp expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UserSessionStatus getStatus() {
        return status;
    }

    public void setStatus(UserSessionStatus status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }
}

