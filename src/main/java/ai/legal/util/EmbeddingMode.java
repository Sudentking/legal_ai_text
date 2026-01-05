package ai.legal.util;

/**
 * 向量生成模式。
 */
public enum EmbeddingMode {
    LEGACY("legacy"),
    HASH_NGRAM_V1("hash_ngram_v1");

    private final String code;

    EmbeddingMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static EmbeddingMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return LEGACY;
        }
        String v = raw.trim().toLowerCase();
        for (EmbeddingMode mode : values()) {
            if (mode.code.equals(v)) {
                return mode;
            }
        }
        return LEGACY;
    }
}

