package ai.legal.rag.prompt;

import ai.legal.model.LegalEmbedding;

import java.util.List;

/**
 * 第二阶段：基于“法律依据”进行受控法律分析与建议。
 */
public class LegalAnalysisPromptBuilder {

    private LegalAnalysisPromptBuilder() {
    }

    /**
     * 构建受控法律分析 Prompt，要求引用法律依据、克制表述，并添加免责声明。
     */
    public static String buildPrompt(String userQuestion, String legalBasis, List<LegalEmbedding> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("角色：你是法律智能助手，需在提供的法律依据框架内做审慎分析与建议。\n");
        sb.append("输入：用户问题 + 已整理的法律依据（禁止添加新法律）。\n");
        sb.append("规则：\n");
        sb.append("1) 仅基于下方“法律依据”和“检索到的条文”进行分析，不得编造新的法律规定。\n");
        sb.append("2) 必须在分析中明确引用对应的条文或条号。\n");
        sb.append("3) 对不确定情形使用“可能/通常/一般情况下”等克制措辞，避免绝对化。\n");
        sb.append("4) 输出结构建议包含：\n");
        sb.append("   - 法律分析：基于法律依据的适用与解释\n");
        sb.append("   - 风险与建议：可行思路、补充信息需求，务必克制\n");
        sb.append("5) 在结尾添加“免责声明：非正式法律意见，仅供参考”。\n\n");
        sb.append("法律依据：\n").append(legalBasis == null ? "" : legalBasis).append("\n\n");
        sb.append("检索到的法律条文（仅供引用，不得新增条文）：\n");
        if (contexts != null) {
            for (int i = 0; i < contexts.size(); i++) {
                LegalEmbedding item = contexts.get(i);
                sb.append(i + 1).append(") ");
                sb.append("law_id=").append(item.getLawId());
                if (item.getArticleNo() != null) {
                    sb.append("，条文编号=").append(item.getArticleNo());
                }
                sb.append("，片段序号=").append(item.getChunkIndex()).append("\n");
                sb.append(item.getContent()).append("\n\n");
            }
        }
        sb.append("用户问题：\n").append(userQuestion == null ? "" : userQuestion).append("\n\n");
        sb.append("请按照规则输出“法律分析与建议”，并附上免责声明。");
        return sb.toString();
    }
}
