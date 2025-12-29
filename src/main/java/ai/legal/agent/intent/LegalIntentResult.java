package ai.legal.agent.intent;

/**
 * 意图分类结果。
 */
public class LegalIntentResult {
    private final LegalIntent intent;
    private final double confidence;
    private final String reason;

    public LegalIntentResult(LegalIntent intent, double confidence, String reason) {
        this.intent = intent;
        this.confidence = confidence;
        this.reason = reason;
    }

    public LegalIntent getIntent() {
        return intent;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }
}
