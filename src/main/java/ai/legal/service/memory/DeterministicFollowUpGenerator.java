package ai.legal.service.memory;

import ai.legal.rag.intent.LegalIntentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ai.legal.service.memory.SessionFactService.KEY_AGE;
import static ai.legal.service.memory.SessionFactService.KEY_DEBTOR_ATTITUDE;
import static ai.legal.service.memory.SessionFactService.KEY_EVIDENCE;
import static ai.legal.service.memory.SessionFactService.KEY_GUARDIAN_CONSENT;
import static ai.legal.service.memory.SessionFactService.KEY_LABOR_EVENT;
import static ai.legal.service.memory.SessionFactService.KEY_LABOR_RELATION;
import static ai.legal.service.memory.SessionFactService.KEY_LABOR_SALARY;
import static ai.legal.service.memory.SessionFactService.KEY_LOAN_AMOUNT;
import static ai.legal.service.memory.SessionFactService.KEY_LOAN_PURPOSE;
import static ai.legal.service.memory.SessionFactService.KEY_OVERDUE;
import static ai.legal.service.memory.SessionFactService.KEY_PAYMENT_METHOD;
import static ai.legal.service.memory.SessionFactService.KEY_RENT_CONTRACT;
import static ai.legal.service.memory.SessionFactService.KEY_RENT_DEPOSIT;
import static ai.legal.service.memory.SessionFactService.KEY_RENT_TERMINATION;

/**
 * 确定性的补充提问生成器：避免依赖 LLM“感觉”，并确保不重复询问已确认事实。
 */
public final class DeterministicFollowUpGenerator {

    private DeterministicFollowUpGenerator() {
    }

    public static String generate(String userQuestion,
                                  LegalIntentType intentType,
                                  Map<String, String> factMap,
                                  String historyFactsText) {
        String q = userQuestion == null ? "" : userQuestion.replaceAll("\\s+", "");
        String normalized = q.toLowerCase(Locale.ROOT);

        Scenario scenario = detectScenario(normalized);
        if (scenario == Scenario.UNKNOWN) {
            return null;
        }

        List<String> questions = new ArrayList<>();
        switch (scenario) {
            case MINOR_LOAN -> {
                addIfMissing(questions, factMap, KEY_AGE, "未成年人具体年龄是多少周岁？");
                addIfMissing(questions, factMap, KEY_LOAN_AMOUNT, "借款/合同金额是多少？款项是否已实际交付（转账/现金）？");
                addIfMissing(questions, factMap, KEY_GUARDIAN_CONSENT, "该行为是否经过法定代理人（父母）同意或事后追认？是否有证据（聊天/签字）？");
            }
            case LOAN -> {
                addIfMissing(questions, factMap, KEY_LOAN_AMOUNT, "欠款/借款金额大概是多少？");
                addIfMissing(questions, factMap, KEY_PAYMENT_METHOD, "款项是如何交付/支付的（微信/支付宝/银行卡/现金）？");
                addIfMissing(questions, factMap, KEY_EVIDENCE, "你目前有哪些证据可以证明借款关系（转账记录/聊天记录/借条/欠条）？");
                addIfMissing(questions, factMap, KEY_OVERDUE, "是否已经到期/逾期？有无约定还款期限或对方承诺还款时间？");
                addIfMissing(questions, factMap, KEY_DEBTOR_ATTITUDE, "对方目前态度如何（承认/拖延/拒绝/失联）？");
            }
            case RENT -> {
                addIfMissing(questions, factMap, KEY_RENT_CONTRACT, "是否有书面/电子租赁合同或聊天记录可证明合同约定？");
                addIfMissing(questions, factMap, KEY_RENT_DEPOSIT, "是否支付押金/租金？金额分别是多少？");
                addIfMissing(questions, factMap, KEY_RENT_TERMINATION, "房东提前解约的理由是什么？是否提前通知、是否按合同约定解除？");
            }
            case LABOR -> {
                addIfMissing(questions, factMap, KEY_LABOR_RELATION, "你与用人单位的劳动关系证据有哪些（劳动合同/工牌/考勤/入职材料/工资发放记录）？");
                addIfMissing(questions, factMap, KEY_LABOR_SALARY, "涉及的工资/加班费/补偿大概金额是多少？是否有工资条或转账记录？");
                addIfMissing(questions, factMap, KEY_LABOR_EVENT, "争议事件是什么（辞退/拖欠工资/工伤/未缴社保），发生时间与关键经过是怎样的？");
            }
        }

        // 控制在 1~3 个具体问题
        List<String> finalQs = questions.stream().filter(s -> s != null && !s.isBlank()).limit(3).toList();
        if (finalQs.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【一、已确认事实】\n");
        sb.append(historyFactsText == null || historyFactsText.isBlank() ? "（暂无）" : historyFactsText.trim());
        sb.append("\n\n");
        sb.append("【二、法律分析（要件/风险点）】\n");
        sb.append(analysisTemplate(scenario)).append("\n\n");
        sb.append("【三、阶段性结论】\n");
        sb.append(stageConclusionTemplate(scenario, intentType)).append("\n\n");
        sb.append("【四、后续建议（需补充的关键事实，1-3 个）】\n");
        for (int i = 0; i < finalQs.size(); i++) {
            sb.append(i + 1).append(". ").append(finalQs.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private static void addIfMissing(List<String> out, Map<String, String> facts, String key, String question) {
        if (out == null || key == null) {
            return;
        }
        String v = facts == null ? null : facts.get(key);
        if (v == null || v.isBlank()) {
            out.add(question);
        }
    }

    private static Scenario detectScenario(String normalizedLowerNoSpace) {
        if (normalizedLowerNoSpace == null || normalizedLowerNoSpace.isBlank()) {
            return Scenario.UNKNOWN;
        }
        String q = normalizedLowerNoSpace;
        boolean minor = q.contains("未成年人") || q.contains("未成年") || q.contains("16周岁") || q.contains("17周岁");
        if (minor && (containsAny(q, "借款", "借钱", "欠钱", "合同") || containsAny(q, "保证", "分期"))) {
            return Scenario.MINOR_LOAN;
        }
        if (containsAny(q, "借款", "借钱", "欠款", "欠钱", "借条", "欠条", "不还", "逾期")) {
            return Scenario.LOAN;
        }
        if (containsAny(q, "租赁", "租房", "房东", "租客", "承租", "出租", "押金", "租金", "退租", "解约", "解除")) {
            return Scenario.RENT;
        }
        if (containsAny(q, "工资", "劳动合同", "加班", "辞退", "社保", "工伤", "仲裁")) {
            return Scenario.LABOR;
        }
        return Scenario.UNKNOWN;
    }

    private static String stageConclusionTemplate(Scenario scenario, LegalIntentType intentType) {
        return switch (scenario) {
            case MINOR_LOAN ->
                    "未成年人签订借款/分期等合同，通常需要结合其年龄、行为性质及法定代理人同意/追认情况判断效力；在现有信息不足时，可以先按“是否属于与其年龄智力相适应的行为、是否经监护人同意/追认、是否已实际交付”来做初步风险评估。";
            case LOAN ->
                    "欠钱不还类纠纷通常先看“借贷关系是否成立 + 款项是否交付 + 还款约定/逾期情况 + 证据强弱”。在现有信息下可以先准备证据、催告并保全沟通记录；若证据链较完整，通常具备诉讼/支付令路径的基础。";
            case RENT ->
                    "房东提前解除租赁合同是否需要赔偿，通常取决于合同约定、解除理由是否成立、是否构成违约以及押金/租金结算情况。现阶段可先基于合同与沟通记录评估违约责任与赔偿范围。";
            case LABOR ->
                    "劳动争议通常先确认劳动关系与关键事实（入职/工资/解除或拖欠经过）。现阶段可先梳理证据链并评估走“协商→投诉/调解→仲裁→诉讼”的路径。";
            case UNKNOWN ->
                    "当前信息不足以形成稳定判断。";
        };
    }

    private static String analysisTemplate(Scenario scenario) {
        return switch (scenario) {
            case MINOR_LOAN -> String.join("\n",
                    "- 核心要件通常包括：未成年人年龄与民事行为能力、合同/借贷性质是否与其年龄智力相适应、法定代理人是否同意/追认、款项是否实际交付与用途。",
                    "- 主要风险点：监护人不同意导致合同效力风险、对方否认借贷合意或否认交付、证据链不足。");
            case LOAN -> String.join("\n",
                    "- 核心要件通常包括：借贷合意（借条/聊天）、款项交付（转账/现金证据）、还款约定与逾期、对方是否承认与是否有履行障碍。",
                    "- 主要风险点：仅有口头借款、转账备注不清、证据分散/缺失、诉讼时效与管辖选择。");
            case RENT -> String.join("\n",
                    "- 核心要件通常包括：租赁合同及解除条款、房东解除理由是否成立、通知程序是否符合约定/法律、押金/租金结算与违约金条款。",
                    "- 主要风险点：合同/聊天记录缺失、解除理由与证据不足、押金扣除理由不明、损失计算不清。");
            case LABOR -> String.join("\n",
                    "- 核心要件通常包括：劳动关系存在（合同/考勤/工资记录）、争议类型（辞退/拖欠/工伤/社保）、金额与发生时间、证据链完整性。",
                    "- 主要风险点：劳动关系无法证明、关键时间节点不清（入离职/通知/仲裁时效）、金额口径不一致。");
            case UNKNOWN -> "当前场景未命中确定性模板，将交由通用追问/分析流程处理。";
        };
    }

    private static boolean containsAny(String text, String... keywords) {
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

    private enum Scenario {
        MINOR_LOAN,
        LOAN,
        RENT,
        LABOR,
        UNKNOWN
    }
}
