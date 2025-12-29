package ai.legal.rag.prompt.template;

import ai.legal.model.LegalCitation;

import java.util.List;

/**
 * 在原 Prompt 基础上追加引用要求。
 */
public class LegalCitationPromptDecorator {

    /**
     * 将原始 Prompt 追加“必须列出引用条文来源”的要求，并给出示例格式。
     *
     * @param originalPrompt 原有 Prompt 文本
     * @param citations      引用列表（可为空）
     * @return 追加后的 Prompt
     */
    public String decorate(String originalPrompt, List<LegalCitation> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append(originalPrompt == null ? "" : originalPrompt);
        sb.append("\n\n回答要求：\n");
        sb.append("回答结论后必须列出引用条文来源，格式示例：\n");
        sb.append("【依据条文】").append("\n");
        sb.append(" - 《民法典》第1条（片段1）").append("\n");
        sb.append(" - 《民法典》第2条第2款（片段3）").append("\n");
        if (citations != null && !citations.isEmpty()) {
            sb.append("可参考的检索来源：").append("\n");
            for (LegalCitation citation : citations) {
                sb.append(" - law_id=").append(citation.getLawId())
                        .append("，条文=").append(nullToEmpty(citation.getArticleNo()))
                        .append("，片段序号=").append(citation.getChunkIndex());
                if (citation.getClauseIndex() != null) {
                    sb.append("，款项=").append(citation.getClauseIndex());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
