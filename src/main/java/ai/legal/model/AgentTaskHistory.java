package ai.legal.model;

import java.sql.Timestamp;

/**
 * 对应 agent_task_history 表的记录。
 */
public class AgentTaskHistory {
    private Long id;
    private String sessionId;
    private String userQuery;
    private String agentState;
    private String intentType;
    private String decisionReason;
    private Timestamp createdAt;

    public AgentTaskHistory() {
    }

    public AgentTaskHistory(String sessionId, String userQuery, String agentState, String intentType, String decisionReason) {
        this.sessionId = sessionId;
        this.userQuery = userQuery;
        this.agentState = agentState;
        this.intentType = intentType;
        this.decisionReason = decisionReason;
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

    public String getAgentState() {
        return agentState;
    }

    public void setAgentState(String agentState) {
        this.agentState = agentState;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
