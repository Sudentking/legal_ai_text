package ai.legal.rag.intent;

/**
 * 意图分类结果。
 */
public class LegalIntentResult {
    private final LegalIntentType type;
    private final double confidence;
    private final String reason;

    public LegalIntentResult(LegalIntentType type, double confidence, String reason) {
        this.type = type;
        this.confidence = confidence;
        this.reason = reason;
    }

    public LegalIntentType getType() {
        return type;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }
}
