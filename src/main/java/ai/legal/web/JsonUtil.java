package ai.legal.web;

import java.util.Collection;
import java.util.Map;

/**
 * 极简 JSON 序列化工具（仅覆盖本项目 Web 雏形所需场景）。
 * <p>
 * 说明：为避免引入额外 Web 框架，这里只做字符串转义与简单结构拼装。
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String obj(Map<String, ?> map) {
        if (map == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    public static String arr(Collection<?> list) {
        if (list == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(value(o));
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return quote(s);
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        if (v instanceof Map<?, ?> m) {
            return obj((Map<String, ?>) m);
        }
        if (v instanceof Collection<?> c) {
            return arr(c);
        }
        return quote(String.valueOf(v));
    }
}

