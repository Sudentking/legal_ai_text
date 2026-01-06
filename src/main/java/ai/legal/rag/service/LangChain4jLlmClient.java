package ai.legal.rag.service;

import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * 使用 LangChain4j 的 ChatLanguageModel 实现现有 LlmClient 接口，避免手写 HTTP 调用。
 */
public class LangChain4jLlmClient implements LlmClient {

    private final ChatLanguageModel model;

    public LangChain4jLlmClient(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public String chat(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        return model.generate(prompt);
    }
}

