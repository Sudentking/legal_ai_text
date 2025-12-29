package ai.legal.rag.service;

/**
 * 大模型客户端接口，便于后续对接不同厂商。
 */
public interface LlmClient {
    /**
     * 发送 Prompt 并获取模型回复。
     *
     * @param prompt 构造好的 Prompt 文本
     * @return 模型回复
     */
    String chat(String prompt);
}
