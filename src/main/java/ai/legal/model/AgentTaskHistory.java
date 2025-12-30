package ai.legal.model;

import java.sql.Timestamp;

/**
 * 对应 agent_task_history 表的记录。
 */
public class AgentTaskHistory {
    private Long id;
    private String sessionId;
    private String userQuery;
    private String intentType;
    private String strategyUsed;
    private String resultStatus;
    private String failReason;
    private Timestamp createdAt;

    public AgentTaskHistory() {
    }

    public AgentTaskHistory(String sessionId, String userQuery, String intentType, String strategyUsed, String resultStatus, String failReason) {
        this.sessionId = sessionId;
        this.userQuery = userQuery;
        this.intentType = intentType;
        this.strategyUsed = strategyUsed;
        this.resultStatus = resultStatus;
        this.failReason = failReason;
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

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public String getStrategyUsed() {
        return strategyUsed;
    }

    public void setStrategyUsed(String strategyUsed) {
        this.strategyUsed = strategyUsed;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
