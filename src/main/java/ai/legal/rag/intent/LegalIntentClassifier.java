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
        // 优先：法条原文查询，允许直接进入 RAG
        if (isLawTextQuery(normalized)) {
            return new LegalIntentResult(LegalIntentType.LAW_TEXT_QUERY, 0.9, "命中法条/章节/全文查询关键词");
        }
        if (containsAny(normalized, "什么是", "定义", "含义", "解释")) {
            return new LegalIntentResult(LegalIntentType.EXPLANATION, 0.8, "命中了解释类关键词");
        }
        if (containsAny(normalized, "适用范围", "适用于哪些", "适用情形")) {
            return new LegalIntentResult(LegalIntentType.APPLICABILITY, 0.8, "命中了适用范围关键词");
        }
        if (containsAny(normalized, "是否", "可否", "算不算", "构成", "满足条件", "属于", "是否成立")) {
            return new LegalIntentResult(LegalIntentType.LEGAL_CONDITION_CHECK, 0.78, "命中条件判断关键词");
        }
        if (containsAny(normalized, "责任", "赔偿", "处罚", "承担", "后果", "法律责任")) {
            return new LegalIntentResult(LegalIntentType.LEGAL_LIABILITY, 0.76, "命中责任/后果类关键词");
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

    private boolean isLawTextQuery(String normalized) {
        // 常见法条关键词与章节/条款/全文请求，优先视为可直接回答的法条查询
        return containsAny(normalized,
                "法典", "刑法", "民法", "民法典", "行政处罚法", "合同法", "侵权责任法",
                "第一章", "第二章", "第1章", "第2章",
                "第一条", "第二条", "第1条", "第2条",
                "全文", "全部内容", "原文", "条文内容", "法条内容", "整章内容", "整条内容");
    }
}
