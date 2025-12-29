package ai.legal.rag.prompt;

import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;

import java.util.List;

/**
 * 第一阶段：仅整理法律依据，不下结论、不给方案。
 */
public class LegalBasisPromptBuilder {

    private LegalBasisPromptBuilder() {
    }

    /**
     * 构建“法律依据”整理 Prompt，仅允许引用检索到的条文。
     */
    public static String buildPrompt(String userQuestion, List<AggregatedLawContext> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("角色：你是法律助手，当前任务是“仅整理法律依据”，不得给出结论、方案或解释。\n");
        sb.append("规则：\n");
        sb.append("1) 只能引用下方提供的法律条文内容，不得引入其他法律或自行编造。\n");
        sb.append("2) 输出为“法律依据列表”，包含法律名称/条号和关键要点摘要。\n");
        sb.append("3) 禁止输出任何裁判结论、风险判断或解决方案。\n\n");
        sb.append("检索到的法律条文：\n");
        if (contexts != null) {
            for (int i = 0; i < contexts.size(); i++) {
                AggregatedLawContext item = contexts.get(i);
                sb.append(i + 1).append(") law_id=").append(item.getLawId())
                        .append("，条文范围=").append(item.getArticleRange())
                        .append("，覆盖说明=").append(item.getCoverageNote()).append("\n");
                sb.append(item.getContent()).append("\n\n");
            }
        }
        sb.append("用户问题：\n").append(userQuestion == null ? "" : userQuestion).append("\n\n");
        sb.append("请仅输出“法律依据列表”，不要给结论或建议。");
        return sb.toString();
    }
}
