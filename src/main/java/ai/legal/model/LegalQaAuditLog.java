package ai.legal.model;

import java.time.Instant;

public class LegalQaAuditLog {
    private String requestId;
    private String userId;
    private String userQuestion;
    private String intentType;
    private String retrievedLawIds;
    private String retrievedArticleNos;
    private String answerText;
    private boolean factInsufficient;
    private Instant createdAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public String getRetrievedLawIds() {
        return retrievedLawIds;
    }

    public void setRetrievedLawIds(String retrievedLawIds) {
        this.retrievedLawIds = retrievedLawIds;
    }

    public String getRetrievedArticleNos() {
        return retrievedArticleNos;
    }

    public void setRetrievedArticleNos(String retrievedArticleNos) {
        this.retrievedArticleNos = retrievedArticleNos;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public boolean isFactInsufficient() {
        return factInsufficient;
    }

    public void setFactInsufficient(boolean factInsufficient) {
        this.factInsufficient = factInsufficient;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
