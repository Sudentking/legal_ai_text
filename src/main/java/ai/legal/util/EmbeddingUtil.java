package ai.legal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * 统一的 Embedding 生成入口：导入与查询必须使用同一实现，避免召回不可控。
 *
 * <p>可通过 System Property 或 application.properties 配置：
 * <ul>
 *   <li>System Property：embedding.mode=legacy|hash_ngram_v1</li>
 *   <li>application.properties：embedding.mode=legacy|hash_ngram_v1</li>
 * </ul>
 */
public final class EmbeddingUtil {

    public static final int VECTOR_DIMENSION = 1536;

    private static final String MODE_KEY = "embedding.mode";
    private static final EmbeddingMode MODE = loadMode();

    private EmbeddingUtil() {
    }

    public static EmbeddingMode getMode() {
        return MODE;
    }

    public static double[] embed(String text) {
        return switch (MODE) {
            case HASH_NGRAM_V1 -> hashNgramEmbedding(text);
            case LEGACY -> legacyEmbedding(text);
        };
    }

    private static EmbeddingMode loadMode() {
        String sys = System.getProperty(MODE_KEY);
        if (sys != null && !sys.isBlank()) {
            return EmbeddingMode.from(sys);
        }
        Properties p = new Properties();
        try (InputStream in = EmbeddingUtil.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                return EmbeddingMode.LEGACY;
            }
            p.load(in);
            return EmbeddingMode.from(p.getProperty(MODE_KEY));
        } catch (IOException e) {
            return EmbeddingMode.LEGACY;
        }
    }

    private static double[] legacyEmbedding(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        String seed = text == null ? "" : text;
        int base = seed.hashCode();
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            vector[i] = (Math.sin(base + i) + 1) * 0.5;
        }
        return vector;
    }

    /**
     * 字符 n-gram 特征哈希向量：不依赖外部模型，但能让“词面相似”在向量空间更接近。
     */
    private static double[] hashNgramEmbedding(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return vector;
        }

        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            addTokenNgrams(vector, token, 3);
        }
        l2NormalizeInPlace(vector);
        return vector;
    }

    private static void addTokenNgrams(double[] vector, String token, int n) {
        String t = token.trim();
        if (t.isEmpty()) {
            return;
        }
        if (t.length() <= n) {
            addFeature(vector, t, 1.0);
            return;
        }
        for (int i = 0; i + n <= t.length(); i++) {
            String gram = t.substring(i, i + n);
            addFeature(vector, gram, 1.0);
        }
    }

    private static void addFeature(double[] vector, String feature, double weight) {
        int hash = feature.hashCode();
        int idx = Math.floorMod(hash, VECTOR_DIMENSION);
        double sign = ((hash >>> 31) == 0) ? 1.0 : -1.0;
        vector[idx] += sign * weight;
    }

    private static void l2NormalizeInPlace(double[] vector) {
        double sum = 0.0;
        for (double v : vector) {
            sum += v * v;
        }
        if (sum <= 0.0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (isCjk(ch) || Character.isLetterOrDigit(ch)) {
                sb.append(ch);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }
}

