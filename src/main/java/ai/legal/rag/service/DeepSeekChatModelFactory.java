package ai.legal.rag.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 从 application.properties 读取 DeepSeek 配置并构建 LangChain4j ChatLanguageModel。
 *
 * <p>用于 CLI/Web 等非 Spring Boot 启动方式，避免重复手写 HTTP 客户端。</p>
 */
public final class DeepSeekChatModelFactory {

    private DeepSeekChatModelFactory() {
    }

    public static ChatLanguageModel create() {
        Properties p = loadProps();
        String apiKey = trim(p.getProperty("deepseek.api.key"));
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = trim(System.getenv("DEEPSEEK_API_KEY"));
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 deepseek.api.key（或环境变量 DEEPSEEK_API_KEY）");
        }
        String baseUrl = trim(p.getProperty("deepseek.base-url"));
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com";
        }
        String model = trim(p.getProperty("deepseek.model"));
        if (model == null || model.isBlank()) {
            model = "deepseek-chat";
        }
        double temperature = parseDouble(p.getProperty("deepseek.temperature"), 0.0);

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    private static Properties loadProps() {
        try (InputStream in = DeepSeekChatModelFactory.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException("未找到 application.properties");
            }
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }

    private static double parseDouble(String v, double def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
