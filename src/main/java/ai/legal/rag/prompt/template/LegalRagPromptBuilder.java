package ai.legal.rag.prompt.template;

public class LegalRagPromptBuilder {

    /**
     * 构建系统提示，定义模型角色与约束。
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是中国法律智能助手。");
        sb.append("只能依据提供的法律条文或司法解释回答，不得编造或扩展法律依据。");
        sb.append("使用专业、克制、非聊天式的法律意见风格作答。");
        sb.append("如提供的条文不足以得出结论，必须明确说明“依据提供的条文不足以得出结论”。");
        return sb.toString();
    }

    /**
     * 构建用户侧提示，包含问题与检索到的法律条文。
     *
     * @param question  用户问题
     * @param lawChunks 检索到的法律条文内容
     * @return 用户 Prompt
     */
    public String buildUserPrompt(String question, String lawChunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：\n");
        sb.append(question == null ? "" : question).append("\n\n");
        sb.append("检索到的法律条文：\n");
        sb.append(lawChunks == null ? "" : lawChunks).append("\n\n");
        sb.append("请仅依据上述条文回答。如条文不足以回答，请明确说明“依据提供的条文不足以得出结论”。");
        return sb.toString();
    }
}
