package ai.legal.service.tool;

import ai.legal.rag.intent.LegalIntentType;

import java.util.Locale;

/**
 * 确定性工具链：用于输出稳定的证据清单/处理路径/风险点模板，避免完全依赖 LLM 生成“看似合理但不稳定”的建议。
 *
 * <p>注意：本工具不输出具体法条编号；条文依据仍由 RAG/结构化检索提供。</p>
 */
public class DeterministicToolchainService {

    public String buildAppendix(String userQuestion, LegalIntentType intentType, String historyFactsText) {
        String q = merge(userQuestion, historyFactsText).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (q.isBlank()) {
            return null;
        }

        Scenario scenario = detectScenario(q);
        if (scenario == Scenario.UNKNOWN) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【确定性工具建议】\n");
        sb.append("【证据清单】\n").append(evidenceChecklist(scenario)).append("\n\n");
        sb.append("【处理路径】\n").append(actionPath(scenario)).append("\n\n");
        sb.append("【风险点】\n").append(riskPoints(scenario)).append("\n");
        return sb.toString().trim();
    }

    private Scenario detectScenario(String normalizedLowerNoSpace) {
        String q = normalizedLowerNoSpace == null ? "" : normalizedLowerNoSpace;
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

    private String evidenceChecklist(Scenario scenario) {
        return switch (scenario) {
            case LOAN -> String.join("\n",
                    "- 转账/收款凭证：微信/支付宝/银行卡流水、转账备注截图",
                    "- 借贷合意证据：聊天记录、通话录音、借条/欠条、对账记录",
                    "- 借款人身份信息：姓名、联系方式、身份证号/住址（尽量）",
                    "- 催告与沟通记录：催款截图、对方承诺还款的证据");
            case RENT -> String.join("\n",
                    "- 租赁合同/补充协议：含租期、租金、押金、解除条款",
                    "- 支付凭证：押金/租金转账记录、收据、发票",
                    "- 沟通与通知：房东解除通知、聊天记录、录音（含时间）",
                    "- 房屋现状证据：交接清单、照片/视频、维修记录（如有）");
            case LABOR -> String.join("\n",
                    "- 劳动关系证据：劳动合同、入职材料、工牌、考勤、工作安排记录",
                    "- 工资证据：工资条、银行/转账记录、个税/社保缴纳记录（如有）",
                    "- 争议事件证据：辞退通知、解除沟通记录、违纪材料（如对方主张）",
                    "- 关键时间点：入职/离职/拖欠期间/受伤时间等的证明材料");
            case UNKNOWN -> "";
        };
    }

    private String actionPath(Scenario scenario) {
        return switch (scenario) {
            case LOAN -> String.join("\n",
                    "1. 固定证据：导出转账凭证与聊天记录（建议带时间戳/原始文件）。",
                    "2. 明确还款：向对方发送书面催告，给出具体还款期限并保留送达/沟通记录。",
                    "3. 协商/调解：可先协商分期或调解，避免成本扩大。",
                    "4. 司法路径：证据链完整时，可考虑起诉或尝试“支付令”（适用条件需自行核对）。",
                    "5. 执行与保全：胜诉或生效后申请执行；必要时考虑财产线索与保全。");
            case RENT -> String.join("\n",
                    "1. 先对合同：核对解除条款与违约金/押金处理约定。",
                    "2. 固定证据：保存解除通知、沟通记录、支付凭证与房屋交接证据。",
                    "3. 先行协商：就押金退还、搬离时间、补偿金额形成书面确认。",
                    "4. 争议处理：协商不成可走调解/诉讼，主张违约责任与损失（依据证据）。");
            case LABOR -> String.join("\n",
                    "1. 固定证据：劳动关系、工资与争议事件相关证据尽量齐全。",
                    "2. 先行沟通：与单位协商并保留记录，避免口头争议。",
                    "3. 行政途径：涉及拖欠工资/社保等，可先投诉或申请调解。",
                    "4. 仲裁优先：多数劳动争议需先走劳动仲裁；注意申请期限。",
                    "5. 诉讼：对仲裁裁决不服或需强制执行时再进入诉讼/执行程序。");
            case UNKNOWN -> "";
        };
    }

    private String riskPoints(Scenario scenario) {
        return switch (scenario) {
            case LOAN -> String.join("\n",
                    "- 证据链不足：仅口头借款或转账备注不清，容易产生举证风险。",
                    "- 借贷性质争议：对方可能主张系货款/赠与/代付等，需要证据排除。",
                    "- 时效与管辖：时间过久、被告所在地不明等会增加诉讼成本与不确定性。");
            case RENT -> String.join("\n",
                    "- 解除理由与程序：房东解除是否有合法/约定依据、通知是否到位。",
                    "- 损失计算：租客实际损失与违约金主张需要证据支撑（搬家费、差价等）。",
                    "- 押金争议：扣押金的理由、房屋损耗界定容易产生争议。");
            case LABOR -> String.join("\n",
                    "- 劳动关系证明不足：无合同/无工资发放记录时风险明显上升。",
                    "- 仲裁期限：超过期限可能影响权利救济（尤其是工资/解除补偿类）。",
                    "- 金额口径不清：加班费/补偿计算需要明确基数、工时与证据。");
            case UNKNOWN -> "";
        };
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

    private enum Scenario {
        LOAN,
        RENT,
        LABOR,
        UNKNOWN
    }
}

