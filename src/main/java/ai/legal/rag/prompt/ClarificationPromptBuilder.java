package ai.legal.rag.prompt;

/**
 * 构建补充事实的澄清 Prompt。
 */
public class ClarificationPromptBuilder {

    private ClarificationPromptBuilder() {
    }

    /**
     * 构造用于向用户收集事实信息的 Prompt。
     *
     * @param userQuestion 用户原始问题
     * @param reason       需要补充事实的原因
     * @return Prompt 文本
     */
    public static String build(String userQuestion, String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("系统角色：你是法律智能助手，当前无法直接作出结论，需要补充关键信息后才能给出法律意见。\n");
        builder.append("说明原因：").append(reason == null ? "信息不足" : reason).append("\n");
        builder.append("请用中文向用户提出1-3个具体问题，以收集必要的事实信息。问题必须具体、可回答，避免开放式聊天，并避免重复询问已确认的事实。\n");
        builder.append("用户原始问题：").append(userQuestion == null ? "" : userQuestion).append("\n");
        builder.append("输出格式：只输出需要补充的具体问题。");
        return builder.toString();
    }
}
