package ai.legal.rag.prompt;

import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;

import java.util.List;

/**
 * 第二阶段：基于“法律依据”进行受控法律分析与建议。
 */
public class LegalAnalysisPromptBuilder {

    private LegalAnalysisPromptBuilder() {
    }

    /**
     * 构建受控法律分析 Prompt，要求引用法律依据、克制表述，并添加免责声明。
     *
     * @param factsSufficient true 表示已达到最低事实集，需给出初步结论与方案
     * @param historyFacts    已确认事实（从多轮对话中汇总），用于提醒模型避免重复追问
     */
    public static String buildPrompt(String userQuestion, String legalBasis, List<AggregatedLawContext> contexts,
                                     boolean factsSufficient, String historyFacts) {
        StringBuilder sb = new StringBuilder();
        boolean hasContexts = contexts != null && !contexts.isEmpty();
        sb.append("角色：你是法律智能助手，需在提供的法律依据框架内做审慎分析与建议。\n");
        sb.append("输入：用户问题 + 已整理的法律依据（禁止添加新法律）。\n");
        sb.append("规则：\n");
        sb.append("1) 仅基于下方“法律依据”和“检索到的条文”进行分析，不得编造新的法律规定。\n");
        if (hasContexts) {
            sb.append("2) 必须在分析中明确引用对应的条文或条号。\n");
        } else {
            sb.append("2) 当前未提供可引用的条文：不得编造条号或引用；必须明确说明“未检索到可引用条文，依据有限”。\n");
        }
        sb.append("3) 对不确定情形使用“可能/通常/一般情况下”等克制措辞，避免绝对化。\n");
        sb.append("4) 输出必须按顺序包含：\n");
        sb.append("   ① 初步法律结论（是否构成民间借贷/债权是否成立/可走的法律路径）。\n");
        sb.append("   ② 明确法条依据（引用具体条文并标注条号）。\n");
        sb.append("   ③ 可执行方案（协商/律师函/起诉/支付令/小额诉讼等，简述条件与流程）。\n");
        sb.append("   ④ 风险与不确定点（证据薄弱、败诉风险等）。\n");
        sb.append("   ⑤ 补充信息建议（可选，说明其用于优化判断，而非作出判断的前置条件）。\n");
        sb.append("5) 如条文覆盖不完整，请在答案中提示“依据有限”。\n");
        sb.append("6) 在结尾添加“免责声明：非正式法律意见，仅供参考”。\n");
        if (factsSufficient) {
            sb.append("7) 当前事实已满足最低判断标准，禁止仅提出追问，必须先给出完整结构的初步意见。\n");
        } else {
            sb.append("7) 若事实仍有不足，也应给出基于已有信息的审慎分析，并标注哪些信息不足。\n");
        }
        sb.append("\n");
        sb.append("已确认事实（从对话历史中汇总，禁止重复追问这些点）：\n")
                .append(historyFacts == null ? "无" : historyFacts).append("\n\n");
        sb.append("法律依据：\n").append(legalBasis == null ? "" : legalBasis).append("\n\n");
        sb.append("检索到的法律条文（仅供引用，不得新增条文）：\n");
        if (hasContexts) {
            for (int i = 0; i < contexts.size(); i++) {
                AggregatedLawContext item = contexts.get(i);
                sb.append(i + 1).append(") law_id=").append(item.getLawId())
                        .append("，条文范围=").append(item.getArticleRange())
                        .append("，覆盖说明=").append(item.getCoverageNote()).append("\n");
                sb.append(item.getContent()).append("\n\n");
            }
        } else {
            sb.append("（无）\n\n");
        }
        sb.append("用户问题：\n").append(userQuestion == null ? "" : userQuestion).append("\n\n");
        sb.append("请按照规则输出“法律分析与建议”，并附上免责声明。");
        return sb.toString();
    }
}
