package ai.legal.rag.service;

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
