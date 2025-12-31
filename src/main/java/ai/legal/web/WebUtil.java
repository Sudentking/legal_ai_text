package ai.legal.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpServer 工具类：读取请求、解析表单/Query/Cookie、输出响应。
 */
public final class WebUtil {

    private WebUtil() {
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    public static Map<String, String> parseQuery(String rawQuery) {
        return parseParams(rawQuery);
    }

    public static Map<String, String> parseForm(String body) {
        return parseParams(body);
    }

    private static Map<String, String> parseParams(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isBlank()) {
            return map;
        }
        String[] pairs = s.split("&");
        for (String p : pairs) {
            if (p.isEmpty()) {
                continue;
            }
            int idx = p.indexOf('=');
            String k = idx >= 0 ? p.substring(0, idx) : p;
            String v = idx >= 0 ? p.substring(idx + 1) : "";
            map.put(urlDecode(k), urlDecode(v));
        }
        return map;
    }

    private static String urlDecode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static Map<String, String> parseCookies(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        if (exchange == null) {
            return map;
        }
        Headers headers = exchange.getRequestHeaders();
        List<String> cookies = headers.get("Cookie");
        if (cookies == null) {
            return map;
        }
        for (String header : cookies) {
            if (header == null || header.isBlank()) {
                continue;
            }
            String[] parts = header.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String name = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                map.put(name, value);
            }
        }
        return map;
    }

    public static void setCookie(HttpExchange exchange, String name, String value, int maxAgeSeconds, boolean httpOnly) {
        if (exchange == null || name == null || name.isBlank()) {
            return;
        }
        String v = value == null ? "" : value;
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(v).append("; Path=/");
        if (maxAgeSeconds >= 0) {
            sb.append("; Max-Age=").append(maxAgeSeconds);
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        exchange.getResponseHeaders().add("Set-Cookie", sb.toString());
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        if (location == null || location.isBlank()) {
            location = "/";
        }
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    public static void sendText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        if (contentType == null || contentType.isBlank()) {
            contentType = "text/plain; charset=utf-8";
        }
        if (body == null) {
            body = "";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    public static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}

