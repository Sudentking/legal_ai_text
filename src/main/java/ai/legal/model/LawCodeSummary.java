package ai.legal.model;

/**
 * 按 law_code 聚合的知识库概览（用于后台管理展示）。
 */
public class LawCodeSummary {
    private String lawCode;
    private String lawTitleSample;
    private long articleCount;
    private long chunkCount;
    private long vectorCount;

    public String getLawCode() {
        return lawCode;
    }

    public void setLawCode(String lawCode) {
        this.lawCode = lawCode;
    }

    public String getLawTitleSample() {
        return lawTitleSample;
    }

    public void setLawTitleSample(String lawTitleSample) {
        this.lawTitleSample = lawTitleSample;
    }

    public long getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(long articleCount) {
        this.articleCount = articleCount;
    }

    public long getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(long chunkCount) {
        this.chunkCount = chunkCount;
    }

    public long getVectorCount() {
        return vectorCount;
    }

    public void setVectorCount(long vectorCount) {
        this.vectorCount = vectorCount;
    }
}

