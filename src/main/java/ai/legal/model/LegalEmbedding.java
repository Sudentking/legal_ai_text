package ai.legal.model;

import java.sql.Timestamp;
import java.util.Arrays;

/**
 * 表示法律文本的向量嵌入记录。
 */
public class LegalEmbedding {
    // 主键
    private long id;
    // 法条 ID
    private long lawId;
    // 条款编号
    private String articleNo;
    // 分片索引
    private int chunkIndex;
    // 向量内容，长度必须为 1536
    private double[] embedding;
    // 文本内容片段
    private String content;
    // 来源标识
    private String source;
    // 创建时间
    private Timestamp createdAt;

    public LegalEmbedding() {
    }

    public LegalEmbedding(long lawId, String articleNo, int chunkIndex, double[] embedding, String content, String source) {
        this.lawId = lawId;
        this.articleNo = articleNo;
        this.chunkIndex = chunkIndex;
        this.embedding = embedding;
        this.content = content;
        this.source = source;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getLawId() {
        return lawId;
    }

    public String getArticleNo() {
        return articleNo;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "LegalEmbedding{" +
                "id=" + id +
                ", lawId=" + lawId +
                ", articleNo='" + articleNo + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", embedding=" + Arrays.toString(sampleEmbeddingPreview()) +
                ", content='" + content + '\'' +
                ", source='" + source + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    // 仅取前几个值用于日志预览，避免打印超长向量
    private double[] sampleEmbeddingPreview() {
        if (embedding == null) {
            return new double[0];
        }
        int length = Math.min(5, embedding.length);
        double[] preview = new double[length];
        System.arraycopy(embedding, 0, preview, 0, length);
        return preview;
    }
}
