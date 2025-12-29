package ai.legal.rag.safety;

/**
 * 法律回答风险等级。
 */
public enum LegalAnswerRiskLevel {
    SAFE_EXPLANATION,
    CONDITIONAL_JUDGMENT,
    INSUFFICIENT_BASIS,
    HIGH_RISK_CONCLUSION
}
