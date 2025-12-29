package ai.legal.rag.intent;

import java.util.Locale;

/**
 * 简单的法律意图识别器，基于关键词规则。
 */
public class LegalIntentClassifier {

    /**
     * 对用户问题进行意图分类。
     *
     * @param question 用户问题
     * @return 分类结果
     */
    public LegalIntentResult classify(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new LegalIntentResult(LegalIntentType.NEEDS_FACTS, 0.2, "问题为空或缺少上下文");
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "什么是", "定义", "含义", "解释")) {
            return new LegalIntentResult(LegalIntentType.EXPLANATION, 0.8, "命中了解释类关键词");
        }
        if (containsAny(normalized, "是否", "可否", "合法吗", "适用", "能否", "可以吗")) {
            return new LegalIntentResult(LegalIntentType.APPLICABILITY, 0.75, "命中了适用性/合法性关键词");
        }
        if (containsAny(normalized, "如何", "怎么", "流程", "步骤", "办理", "需要什么材料", "怎么做")) {
            return new LegalIntentResult(LegalIntentType.PROCEDURE, 0.7, "命中了流程类关键词");
        }
        // 长度过短或缺少事实信息
        if (question.length() < 6) {
            return new LegalIntentResult(LegalIntentType.NEEDS_FACTS, 0.5, "问题过短，可能缺少事实");
        }
        return new LegalIntentResult(LegalIntentType.NEEDS_FACTS, 0.4, "未命中明确模式，建议补充事实");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
