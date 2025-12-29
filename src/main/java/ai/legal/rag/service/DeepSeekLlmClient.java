package ai.legal.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * DeepSeek 大模型客户端：读取 application.properties 中的 deepseek.api.key，调用 Chat Completion。
 */
public class DeepSeekLlmClient implements LlmClient {

    private static final String ENDPOINT = "https://api.deepseek.com/v1/chat/completions";
    private static final String MODEL = "deepseek-chat";
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_TOKENS = 1024;

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekLlmClient() {
        this.apiKey = loadApiKey();
    }

    @Override
    public String chat(String prompt) {
        return generate("", prompt);
    }

    /**
     * 调用 DeepSeek Chat Completion，传入 system + user 两段 prompt，返回模型文本。
     */
    public String generate(String systemPrompt, String userPrompt) {
        try {
            String body = buildRequestBody(systemPrompt, userPrompt);
            HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new RuntimeException("DeepSeek API 调用失败: HTTP " + code + " body=" + resp);
            }
            return parseContent(resp);
        } catch (IOException e) {
            throw new RuntimeException("调用 DeepSeek API 异常: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) throws IOException {
        String sys = systemPrompt == null ? "" : systemPrompt;
        String usr = userPrompt == null ? "" : userPrompt;
        JsonNode root = mapper.createObjectNode()
                .put("model", MODEL)
                .put("temperature", TEMPERATURE)
                .put("max_tokens", MAX_TOKENS)
                .set("messages", mapper.createArrayNode()
                        .add(mapper.createObjectNode()
                                .put("role", "system")
                                .put("content", sys))
                        .add(mapper.createObjectNode()
                                .put("role", "user")
                                .put("content", usr)));
        return mapper.writeValueAsString(root);
    }

    private String parseContent(String resp) throws IOException {
        JsonNode root = mapper.readTree(resp);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new RuntimeException("DeepSeek 响应缺少 content: " + resp);
        }
        return content.asText();
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private String loadApiKey() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException("未找到 application.properties");
            }
            Properties p = new Properties();
            p.load(in);
            String key = p.getProperty("deepseek.api.key");
            if (key == null || key.isEmpty()) {
                throw new IllegalStateException("未配置 deepseek.api.key");
            }
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("读取 application.properties 失败", e);
        }
    }
}
