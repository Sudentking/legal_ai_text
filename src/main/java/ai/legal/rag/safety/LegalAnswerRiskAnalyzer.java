package ai.legal.rag.safety;

import ai.legal.agent.intent.LegalIntent;
import ai.legal.model.LegalEmbedding;

import java.util.List;
import java.util.Locale;

/**
 * 基于规则的法律回答风险分析器。
 */
public class LegalAnswerRiskAnalyzer {

    /**
     * 根据问题、检索条文、意图给出风险等级。
     *
     * @param userQuestion 用户问题
     * @param contexts     检索到的条文
     * @param intent       法律意图
     * @return 风险等级
     */
    public LegalAnswerRiskLevel assess(String userQuestion, List<LegalEmbedding> contexts, LegalIntent intent) {
        if (intent == null) {
            return LegalAnswerRiskLevel.INSUFFICIENT_BASIS;
        }
        // 若检索结果为空或问题过短，视为依据不足
        if (contexts == null || contexts.isEmpty() || userQuestion == null || userQuestion.trim().length() < 5) {
            return LegalAnswerRiskLevel.INSUFFICIENT_BASIS;
        }
        String normalized = userQuestion.toLowerCase(Locale.ROOT);
        // 条件、责任类问题默认判为条件性
        if (intent == LegalIntent.LEGAL_CONDITION_CHECK || intent == LegalIntent.LEGAL_LIABILITY) {
            return LegalAnswerRiskLevel.CONDITIONAL_JUDGMENT;
        }
        // 程序性和解释性相对安全
        if (intent == LegalIntent.LEGAL_PROCEDURE || intent == LegalIntent.LEGAL_EXPLANATION) {
            return LegalAnswerRiskLevel.SAFE_EXPLANATION;
        }
        // 适用范围类，若包含“必须”“一定”等绝对词，提升风险
        if (intent == LegalIntent.LEGAL_SCOPE) {
            if (containsAny(normalized, "必须", "一定", "当然", "绝对")) {
                return LegalAnswerRiskLevel.CONDITIONAL_JUDGMENT;
            }
            return LegalAnswerRiskLevel.SAFE_EXPLANATION;
        }
        // 其他未识别场景降级为依据不足
        return LegalAnswerRiskLevel.INSUFFICIENT_BASIS;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
