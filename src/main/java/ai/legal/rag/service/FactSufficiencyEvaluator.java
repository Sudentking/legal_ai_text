package ai.legal.rag.service;

import ai.legal.rag.intent.LegalIntentType;

import java.util.Locale;

/**
 * 判断用户描述的事实是否已达到“最低可给出初步法律意见”的程度。
 * 现版本聚焦于民间借贷场景，后续可扩展其他场景。
 */
public class FactSufficiencyEvaluator {

    /**
     * 是否满足最低事实集（借贷场景）。
     *
     * @param question 用户问题
     * @return true 表示事实已足够，可直接给出初步意见；false 表示仍需补充
     */
    public boolean isLoanFactsSufficient(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean loanScene = containsAny(q, "借款", "欠款", "借了", "借钱", "欠钱", "借条", "欠条");
        if (!loanScene) {
            return false;
        }
        int score = 0;
        if (containsAny(q, "转账", "流水", "打款", "收据", "凭证")) {
            score++;
        }
        if (containsAny(q, "承认借", "承诺还", "同意还", "口头承认", "约定还款")) {
            score++;
        }
        if (containsAny(q, "逾期", "超过期限", "到期未还", "未按时", "期限到了")) {
            score++;
        }
        if (containsAny(q, "拒绝还", "不还", "不肯还", "失联", "联系不到", "躲避")) {
            score++;
        }
        // 具备借贷场景且出现至少两类佐证信号，视为可给出初步判断
        return score >= 2;
    }

    /**
     * 多场景事实充分性判断：用于 Agent 是否进入 FACT_CHECK 的确定性依据。
     *
     * <p>策略：
     * - 借贷/欠款：要求至少出现两类关键事实信号
     * - 租赁/房东：要求至少出现两类关键事实信号
     * - 其他场景：默认视为“可以先判断”（返回 true），避免无限追问
     */
    public boolean isFactsSufficient(LegalIntentType intentType, String userQuestion, String historyFactsText) {
        String merged = merge(userQuestion, historyFactsText);
        if (merged.isBlank()) {
            return false;
        }
        String q = merged.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

        if (containsAny(q, "借款", "欠款", "借了", "借钱", "欠钱", "借条", "欠条")) {
            return isLoanFactsSufficient(q);
        }
        if (containsAny(q, "租赁", "租房", "房东", "租客", "承租", "出租", "押金", "租金", "解约", "解除合同", "退租")) {
            return isRentFactsSufficient(q);
        }
        if (containsAny(q, "工资", "劳动合同", "加班", "辞退", "社保", "工伤", "仲裁")) {
            return isLaborFactsSufficient(q);
        }
        // 默认：未知场景不强制追问，交给 RAG/LLM 做阶段性结论
        return true;
    }

    private boolean isRentFactsSufficient(String q) {
        int score = 0;
        if (containsAny(q, "合同", "租赁合同", "租房合同", "签了", "书面")) {
            score++;
        }
        if (containsAny(q, "押金", "租金", "金额", "每月")) {
            score++;
        }
        if (containsAny(q, "提前解除", "提前解约", "解除", "退租", "违约")) {
            score++;
        }
        if (containsAny(q, "争议", "不退", "不搬", "拒绝", "不给", "扣押金")) {
            score++;
        }
        return score >= 2;
    }

    private boolean isLaborFactsSufficient(String q) {
        int score = 0;
        if (containsAny(q, "劳动合同", "入职", "试用期", "工牌", "考勤")) {
            score++;
        }
        if (containsAny(q, "工资", "薪资", "拖欠", "未发", "加班费")) {
            score++;
        }
        if (containsAny(q, "辞退", "解除", "离职", "开除")) {
            score++;
        }
        if (containsAny(q, "证据", "聊天记录", "工资条", "转账", "流水")) {
            score++;
        }
        return score >= 2;
    }

    private String merge(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        if (x.isEmpty()) {
            return y;
        }
        if (y.isEmpty()) {
            return x;
        }
        return x + "\n" + y;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
