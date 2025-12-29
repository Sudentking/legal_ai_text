package ai.legal.agent.intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 基于关键词规则的法律意图分类器。
 */
public class LegalIntentClassifier {

    private final List<Rule> rules = new ArrayList<>();

    public LegalIntentClassifier() {
        // 按优先级添加规则
        rules.add(new Rule(LegalIntent.LEGAL_SCOPE, 0.85, "命中适用范围关键词", "适用范围", "适用于哪些", "范围", "适用情形"));
        rules.add(new Rule(LegalIntent.LEGAL_EXPLANATION, 0.8, "命中解释类关键词", "什么意思", "含义", "解释", "是什么", "定义"));
        rules.add(new Rule(LegalIntent.LEGAL_CONDITION_CHECK, 0.78, "命中条件判断关键词", "是否", "可否", "算不算", "构成", "满足条件"));
        rules.add(new Rule(LegalIntent.LEGAL_LIABILITY, 0.76, "命中责任类关键词", "责任", "赔偿", "罚", "违法责任", "承担什么"));
        rules.add(new Rule(LegalIntent.LEGAL_PROCEDURE, 0.74, "命中程序类关键词", "如何", "怎么", "流程", "步骤", "起诉", "申请", "办理"));
    }

    /**
     * 对用户问题进行法律意图分类。
     *
     * @param question 用户问题
     * @return 分类结果
     */
    public LegalIntentResult classify(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new LegalIntentResult(LegalIntent.FACT_INSUFFICIENT, 0.2, "问题为空或缺乏上下文");
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        for (Rule rule : rules) {
            if (rule.matches(normalized)) {
                return new LegalIntentResult(rule.intent, rule.confidence, rule.reason);
            }
        }
        if (question.length() < 6) {
            return new LegalIntentResult(LegalIntent.FACT_INSUFFICIENT, 0.4, "问题过短，信息不足");
        }
        return new LegalIntentResult(LegalIntent.UNKNOWN, 0.3, "未命中任何规则");
    }

    private static class Rule {
        private final LegalIntent intent;
        private final double confidence;
        private final String reason;
        private final String[] keywords;

        Rule(LegalIntent intent, double confidence, String reason, String... keywords) {
            this.intent = intent;
            this.confidence = confidence;
            this.reason = reason;
            this.keywords = keywords;
        }

        boolean matches(String text) {
            if (text == null) {
                return false;
            }
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isEmpty() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
