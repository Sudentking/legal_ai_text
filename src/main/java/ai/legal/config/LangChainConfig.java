package ai.legal.config;

import ai.legal.rag.service.LlmClient;
import ai.legal.rag.service.LangChain4jLlmClient;
import ai.legal.rag.service.LegalAssistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置：使用 OpenAI Connector 连接 DeepSeek OpenAI-compatible API。
 *
 * <p>关键配置项：
 * <ul>
 *   <li>deepseek.base-url（默认 https://api.deepseek.com）</li>
 *   <li>deepseek.api.key（必填）</li>
 *   <li>deepseek.model（默认 deepseek-chat）</li>
 *   <li>deepseek.temperature（默认 0.0）</li>
 * </ul>
 */
@Configuration
public class LangChainConfig {

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String modelName;

    @Value("${deepseek.temperature:0.0}")
    private double temperature;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 deepseek.api.key（请在 application.properties 或环境变量中设置）");
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    /**
     * 兼容现有代码的 LlmClient 适配层：内部委托给 LangChain4j ChatLanguageModel。
     */
    @Bean
    public LlmClient llmClient(ChatLanguageModel model) {
        return new LangChain4jLlmClient(model);
    }

    @Bean
    public LegalAssistant legalAssistant(ChatLanguageModel model) {
        return AiServices.builder(LegalAssistant.class)
                .chatLanguageModel(model)
                .build();
    }
}
