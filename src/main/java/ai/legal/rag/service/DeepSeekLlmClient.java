package ai.legal.rag.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * DeepSeek 大模型客户端实现。
 */
public class DeepSeekLlmClient implements LlmClient {

    private static final String ENDPOINT = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-chat";
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_TOKENS = 1024;
    private final HttpClient httpClient;
    private final String apiKey;

    public DeepSeekLlmClient(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("DeepSeek API Key 不能为空");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * 兼容现有接口：仅传单一 Prompt 时使用空的 systemPrompt。
     */
    @Override
    public String chat(String prompt) {
        return generate("", prompt);
    }

    /**
     * 调用 DeepSeek Chat Completions。
     *
     * @param systemPrompt 系统提示
     * @param userPrompt   用户问题
     * @return 模型回复文本
     */
    public String generate(String systemPrompt, String userPrompt) {
        try {
            String body = buildRequestBody(systemPrompt, userPrompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("DeepSeek API 调用失败，HTTP " + response.statusCode() + ": " + response.body());
            }
            String content = parseContent(response.body());
            if (content == null) {
                throw new RuntimeException("DeepSeek API 响应缺少内容: " + response.body());
            }
            return content;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("调用 DeepSeek API 异常: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        String sys = systemPrompt == null ? "" : systemPrompt;
        String usr = userPrompt == null ? "" : userPrompt;
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(MODEL).append("\",");
        sb.append("\"temperature\":").append(TEMPERATURE).append(",");
        sb.append("\"max_tokens\":").append(MAX_TOKENS).append(",");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":").append(toJsonString(sys)).append("},");
        sb.append("{\"role\":\"user\",\"content\":").append(toJsonString(usr)).append("}");
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String toJsonString(String text) {
        String escaped = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + escaped + "\"";
    }

    /**
     * 简单解析 choices[0].message.content。
     */
    private String parseContent(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        int choicesIdx = responseBody.indexOf("\"choices\"");
        if (choicesIdx < 0) {
            return null;
        }
        int contentKey = responseBody.indexOf("\"content\"", choicesIdx);
        if (contentKey < 0) {
            return null;
        }
        int colon = responseBody.indexOf(':', contentKey);
        if (colon < 0) {
            return null;
        }
        int startQuote = responseBody.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        boolean escape = false;
        for (int i = startQuote + 1; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);
            if (escape) {
                switch (c) {
                    case '"':
                        content.append('"');
                        break;
                    case '\\':
                        content.append('\\');
                        break;
                    case 'n':
                        content.append('\n');
                        break;
                    case 'r':
                        break;
                    case 't':
                        content.append('\t');
                        break;
                    default:
                        content.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
            }
        }
        return content.toString();
    }
}
