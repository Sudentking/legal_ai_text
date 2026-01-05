package ai.legal.service.memory;

import ai.legal.dao.mysql.SessionFactDao;
import ai.legal.model.SessionFact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话事实服务：从多轮对话中沉淀“已确认事实”，用于避免重复追问与提升连续推理能力。
 *
 * <p>原则：尽量采用确定性规则抽取；抽取失败时不影响主流程。
 */
public class SessionFactService {

    // Canonical keys
    public static final String KEY_AGE = "person.age";
    public static final String KEY_LOAN_AMOUNT = "loan.amount";
    public static final String KEY_LOAN_PURPOSE = "loan.purpose";
    public static final String KEY_GUARDIAN_CONSENT = "minor.guardian_consent";
    public static final String KEY_PAYMENT_METHOD = "payment.method";
    public static final String KEY_EVIDENCE = "evidence";
    public static final String KEY_OVERDUE = "loan.overdue";
    public static final String KEY_DEBTOR_ATTITUDE = "debtor.attitude";
    public static final String KEY_RENT_CONTRACT = "rent.contract";
    public static final String KEY_RENT_DEPOSIT = "rent.deposit";
    public static final String KEY_RENT_TERMINATION = "rent.termination";
    public static final String KEY_LABOR_RELATION = "labor.relation";
    public static final String KEY_LABOR_SALARY = "labor.salary";
    public static final String KEY_LABOR_EVENT = "labor.event";

    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*(\\d+)[\\.|、|\\)]\\s*(.+)\\s*$");
    private static final Pattern AGE_PATTERN = Pattern.compile("(\\d{1,2})\\s*周?岁");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(元|块|人民币)?");

    private final SessionFactDao dao;

    public SessionFactService(SessionFactDao dao) {
        this.dao = dao;
    }

    public Map<String, String> getFactMap(String sessionId, int limit) {
        List<SessionFact> facts = dao.findBySession(sessionId, limit);
        Map<String, String> map = new LinkedHashMap<>();
        for (SessionFact f : facts) {
            if (f == null || f.getFactKey() == null) {
                continue;
            }
            map.put(f.getFactKey(), f.getFactValue());
        }
        return map;
    }

    /**
     * 构建给 Prompt 使用的“已确认事实”文本（可读性优先）。
     */
    public String buildHistoryFactsText(String sessionId, int limit) {
        Map<String, String> facts = getFactMap(sessionId, limit);
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : facts.entrySet()) {
            String label = labelOf(e.getKey());
            String value = e.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            sb.append(label).append("：").append(value.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 尝试从“上一轮 Agent 提问 + 本轮用户回答”中抽取事实并写入 session_fact。
     */
    public void ingestUserTurn(String sessionId, String userMessage, String lastAgentMessage) {
        if (sessionId == null || sessionId.isBlank() || userMessage == null || userMessage.isBlank()) {
            return;
        }

        // 1) 若上一轮包含编号问题，优先按“问题-答案对齐”抽取
        List<String> questions = extractNumberedItems(lastAgentMessage);
        if (!questions.isEmpty()) {
            List<String> answers = splitAnswers(userMessage, questions.size());
            if (!answers.isEmpty()) {
                int n = Math.min(questions.size(), answers.size());
                for (int i = 0; i < n; i++) {
                    String q = questions.get(i);
                    String a = answers.get(i);
                    String key = inferKeyFromQuestion(q);
                    if (key != null) {
                        upsert(sessionId, key, normalizeValue(key, a), "USER", userMessage);
                    }
                }
            }
        }

        // 2) 再做一些通用模式抽取（不依赖上一轮问题）
        extractGenericFacts(sessionId, userMessage);
    }

    /**
     * 支持将外部传入的 historyFacts（key：value 行）写入 session_fact，便于 CLI/Web 兼容。
     */
    public void ingestHistoryFactsText(String sessionId, String historyFactsText) {
        if (sessionId == null || sessionId.isBlank() || historyFactsText == null || historyFactsText.isBlank()) {
            return;
        }
        String[] lines = historyFactsText.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = trimmed.indexOf('：');
            if (idx < 0) {
                idx = trimmed.indexOf(':');
            }
            if (idx <= 0 || idx >= trimmed.length() - 1) {
                continue;
            }
            String k = trimmed.substring(0, idx).trim();
            String v = trimmed.substring(idx + 1).trim();
            String key = canonicalizeLabelKey(k);
            if (key != null && !v.isBlank()) {
                upsert(sessionId, key, v, "SYSTEM", "historyFacts");
            }
        }
    }

    private void extractGenericFacts(String sessionId, String userMessage) {
        String u = userMessage.replaceAll("\\s+", "");

        Matcher age = AGE_PATTERN.matcher(u);
        if (age.find()) {
            upsert(sessionId, KEY_AGE, age.group(1) + "周岁", "USER", userMessage);
        }

        // 仅当出现明显金额信号时才写入，避免把“第几条”误识别为金额
        if (u.contains("元") || u.contains("块") || u.contains("金额")) {
            Matcher m = AMOUNT_PATTERN.matcher(u);
            if (m.find()) {
                String num = m.group(1);
                if (num != null && !num.isBlank()) {
                    upsert(sessionId, KEY_LOAN_AMOUNT, num + "元", "USER", userMessage);
                }
            }
        }

        if (containsAny(u, "父母同意", "家长同意", "法定代理人同意")) {
            upsert(sessionId, KEY_GUARDIAN_CONSENT, "是", "USER", userMessage);
        } else if (containsAny(u, "父母不同意", "家长不同意", "法定代理人不同意")) {
            upsert(sessionId, KEY_GUARDIAN_CONSENT, "否", "USER", userMessage);
        }

        if (containsAny(u, "微信转账", "支付宝", "银行卡", "银行转账", "现金")) {
            upsert(sessionId, KEY_PAYMENT_METHOD, extractPaymentMethod(u), "USER", userMessage);
        }

        if (containsAny(u, "聊天记录", "借条", "欠条", "转账记录", "流水", "收据", "录音")) {
            upsert(sessionId, KEY_EVIDENCE, extractEvidence(u), "USER", userMessage);
        }

        if (containsAny(u, "逾期", "到期未还", "超过期限", "未按时")) {
            upsert(sessionId, KEY_OVERDUE, "是", "USER", userMessage);
        }
        if (containsAny(u, "不还", "拒绝还", "不肯还", "失联", "联系不到", "拉黑")) {
            upsert(sessionId, KEY_DEBTOR_ATTITUDE, "拒不履行/失联", "USER", userMessage);
        } else if (containsAny(u, "承认", "承诺还", "同意还")) {
            upsert(sessionId, KEY_DEBTOR_ATTITUDE, "承认/承诺还款", "USER", userMessage);
        }
    }

    private void upsert(String sessionId, String key, String value, String sourceType, String sourceMessage) {
        if (dao == null) {
            return;
        }
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        dao.upsert(sessionId, key, value, sourceType, sourceMessage);
    }

    private List<String> extractNumberedItems(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher m = NUMBERED_LINE.matcher(line);
            if (m.find()) {
                items.add(m.group(2));
            }
        }
        return items;
    }

    private List<String> splitAnswers(String userMessage, int expected) {
        List<String> answers = new ArrayList<>();
        if (userMessage == null || userMessage.isBlank()) {
            return answers;
        }

        // 1) 若用户也用编号回答，则按编号解析
        List<String> numbered = extractNumberedItems(userMessage);
        if (!numbered.isEmpty()) {
            return numbered;
        }

        // 2) 否则按常见分隔符拆分
        String[] parts = userMessage.split("[，,；;\\n\\t]+");
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (!t.isEmpty()) {
                answers.add(t);
            }
        }
        if (expected > 0 && answers.size() > expected) {
            return answers.subList(0, expected);
        }
        return answers;
    }

    private String inferKeyFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (containsAny(q, "年龄", "几岁", "多少周岁")) {
            return KEY_AGE;
        }
        if (containsAny(q, "金额", "多少钱", "多少元", "数额")) {
            return KEY_LOAN_AMOUNT;
        }
        if (containsAny(q, "用途", "用来", "干什么")) {
            return KEY_LOAN_PURPOSE;
        }
        if (containsAny(q, "法定代理人", "父母", "家长", "同意", "追认")) {
            return KEY_GUARDIAN_CONSENT;
        }
        if (containsAny(q, "转账", "支付方式", "付款方式", "怎么支付", "现金", "微信", "支付宝")) {
            return KEY_PAYMENT_METHOD;
        }
        if (containsAny(q, "证据", "聊天记录", "借条", "欠条", "凭证")) {
            return KEY_EVIDENCE;
        }
        if (containsAny(q, "逾期", "到期", "期限", "拖欠")) {
            return KEY_OVERDUE;
        }
        if (containsAny(q, "态度", "是否承认", "是否拒绝", "是否失联", "对方")) {
            return KEY_DEBTOR_ATTITUDE;
        }
        if (containsAny(q, "租赁合同", "租房合同", "租赁", "租房") && containsAny(q, "合同", "聊天记录", "约定")) {
            return KEY_RENT_CONTRACT;
        }
        if (containsAny(q, "押金", "租金")) {
            return KEY_RENT_DEPOSIT;
        }
        if (containsAny(q, "提前解约", "提前解除", "提前解租", "解除") && containsAny(q, "房东", "理由", "通知")) {
            return KEY_RENT_TERMINATION;
        }
        if (containsAny(q, "劳动关系", "劳动合同", "工牌", "考勤", "入职")) {
            return KEY_LABOR_RELATION;
        }
        if (containsAny(q, "工资", "薪资", "加班费", "补偿") && containsAny(q, "金额", "多少", "大概")) {
            return KEY_LABOR_SALARY;
        }
        if (containsAny(q, "争议事件", "辞退", "解除", "拖欠工资", "工伤", "社保")) {
            return KEY_LABOR_EVENT;
        }
        return null;
    }

    private String normalizeValue(String key, String rawAnswer) {
        if (rawAnswer == null) {
            return null;
        }
        String a = rawAnswer.trim();
        if (a.isEmpty()) {
            return a;
        }
        if (KEY_GUARDIAN_CONSENT.equals(key) || KEY_OVERDUE.equals(key)) {
            String lower = a.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (containsAny(lower, "是", "同意", "有", "经过", "已")) {
                return "是";
            }
            if (containsAny(lower, "否", "不同意", "没有", "未")) {
                return "否";
            }
        }
        if (KEY_LOAN_AMOUNT.equals(key)) {
            String digits = a.replaceAll("[^0-9.]", "");
            if (!digits.isEmpty()) {
                return digits + "元";
            }
        }
        if (KEY_AGE.equals(key)) {
            Matcher m = AGE_PATTERN.matcher(a);
            if (m.find()) {
                return m.group(1) + "周岁";
            }
        }
        return a;
    }

    private String labelOf(String key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case KEY_AGE -> "年龄";
            case KEY_LOAN_AMOUNT -> "金额";
            case KEY_LOAN_PURPOSE -> "用途";
            case KEY_GUARDIAN_CONSENT -> "法定代理人同意/追认";
            case KEY_PAYMENT_METHOD -> "支付方式";
            case KEY_EVIDENCE -> "证据情况";
            case KEY_OVERDUE -> "是否逾期";
            case KEY_DEBTOR_ATTITUDE -> "对方态度";
            case KEY_RENT_CONTRACT -> "租赁合同/约定证据";
            case KEY_RENT_DEPOSIT -> "押金/租金";
            case KEY_RENT_TERMINATION -> "提前解约情况";
            case KEY_LABOR_RELATION -> "劳动关系证据";
            case KEY_LABOR_SALARY -> "工资/补偿金额";
            case KEY_LABOR_EVENT -> "劳动争议事件";
            default -> key;
        };
    }

    private String canonicalizeLabelKey(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String k = label.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (containsAny(k, "年龄", "周岁", "几岁")) {
            return KEY_AGE;
        }
        if (containsAny(k, "金额", "数额", "多少钱")) {
            return KEY_LOAN_AMOUNT;
        }
        if (containsAny(k, "用途")) {
            return KEY_LOAN_PURPOSE;
        }
        if (containsAny(k, "法定代理人", "父母", "家长")) {
            return KEY_GUARDIAN_CONSENT;
        }
        if (containsAny(k, "支付", "转账", "付款方式")) {
            return KEY_PAYMENT_METHOD;
        }
        if (containsAny(k, "证据", "聊天记录", "借条", "欠条")) {
            return KEY_EVIDENCE;
        }
        if (containsAny(k, "逾期", "期限")) {
            return KEY_OVERDUE;
        }
        if (containsAny(k, "态度", "对方")) {
            return KEY_DEBTOR_ATTITUDE;
        }
        if (containsAny(k, "租赁合同", "租房合同", "合同约定", "约定证据")) {
            return KEY_RENT_CONTRACT;
        }
        if (containsAny(k, "押金", "租金")) {
            return KEY_RENT_DEPOSIT;
        }
        if (containsAny(k, "提前解约", "提前解除", "解除情况", "解约理由")) {
            return KEY_RENT_TERMINATION;
        }
        if (containsAny(k, "劳动关系", "劳动合同", "入职", "考勤")) {
            return KEY_LABOR_RELATION;
        }
        if (containsAny(k, "工资", "薪资", "加班费", "补偿")) {
            return KEY_LABOR_SALARY;
        }
        if (containsAny(k, "争议事件", "辞退", "拖欠", "工伤", "社保")) {
            return KEY_LABOR_EVENT;
        }
        return null;
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

    private String extractPaymentMethod(String text) {
        if (text == null) {
            return "";
        }
        if (text.contains("微信")) {
            return "微信";
        }
        if (text.contains("支付宝")) {
            return "支付宝";
        }
        if (text.contains("银行卡") || text.contains("银行")) {
            return "银行卡/银行转账";
        }
        if (text.contains("现金")) {
            return "现金";
        }
        return "其他";
    }

    private String extractEvidence(String text) {
        if (text == null) {
            return "";
        }
        List<String> items = new ArrayList<>();
        if (text.contains("聊天记录")) items.add("聊天记录");
        if (text.contains("借条")) items.add("借条");
        if (text.contains("欠条")) items.add("欠条");
        if (text.contains("转账") || text.contains("流水")) items.add("转账/流水");
        if (text.contains("收据")) items.add("收据");
        if (text.contains("录音")) items.add("录音");
        if (items.isEmpty()) {
            return "有证据";
        }
        return String.join("、", items);
    }
}
