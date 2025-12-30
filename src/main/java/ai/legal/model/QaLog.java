package ai.legal.model;

import java.sql.Timestamp;

/**
 * 问答日志实体，对应日志表（含 status/错误信息）。
 */
public class QaLog {
    private Long id;
    private Long userId;
    private String sessionId;
    private String question;
    private String answer;
    private String agentState;
    private String status; // PENDING / SUCCESS / FAIL
    private String errorMessage;
    private Timestamp createdAt;

    public QaLog() {
    }

    public QaLog(Long userId, String sessionId, String question, String status, String agentState) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.question = question;
        this.status = status;
        this.agentState = agentState;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getAgentState() {
        return agentState;
    }

    public void setAgentState(String agentState) {
        this.agentState = agentState;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
