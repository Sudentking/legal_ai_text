package ai.legal.rag.prompt;

import ai.legal.model.LegalEmbedding;

import java.util.List;

/**
 * 构建适用于法律 RAG 的大模型 Prompt。
 */
public class LegalRagPromptBuilder {

    private LegalRagPromptBuilder() {
    }

    /**
     * 生成遵循法律约束的 Prompt，限制模型只能基于提供的法律条文作答。
     *
     * @param userQuestion 用户问题
     * @param contexts     检索到的法律条文片段
     * @return Prompt 文本
     */
    public static String buildPrompt(String userQuestion, List<LegalEmbedding> contexts) {
        StringBuilder builder = new StringBuilder();
        builder.append("系统角色：你是法律智能助手，只能依据提供的法律条文或司法解释回答问题，给出专业、严谨的法律意见。\n");
        builder.append("回答规则：\n");
        builder.append("1) 仅使用下方“检索到的法律条文”信息作答，不得添加或推断未给出的内容。\n");
        builder.append("2) 不得编造法律结论；如条文不足以得出结论，必须明确说明“依据提供的条文不足以得出结论”。\n");
        builder.append("3) 输出语言为中文，结构清晰、专业，避免聊天语气。\n");
        builder.append("\n检索到的法律条文：\n");
        if (contexts != null) {
            for (int i = 0; i < contexts.size(); i++) {
                LegalEmbedding item = contexts.get(i);
                builder.append(i + 1).append(") ");
                builder.append("law_id=").append(item.getLawId());
                if (item.getArticleNo() != null) {
                    builder.append("，条文编号=").append(item.getArticleNo());
                }
                builder.append("，片段序号=").append(item.getChunkIndex()).append("\n");
                builder.append(item.getContent()).append("\n\n");
            }
        }
        builder.append("用户问题：\n").append(userQuestion == null ? "" : userQuestion).append("\n\n");
        builder.append("请按照上述规则作答。");
        return builder.toString();
    }
}
