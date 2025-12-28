package ai.legal.model;

import java.sql.Timestamp;

/**
 * 对应 MySQL law_text_chunk 表的切片记录。
 */
public class LawTextChunk {
    // 切片ID
    private long id;
    // 原法律条文ID
    private long documentId;
    // 向量ID，对应 legal_embedding.id
    private String vectorId;
    // 切片文本
    private String chunkText;
    // 切片顺序
    private int chunkOrder;
    // 创建时间
    private Timestamp createdAt;

    public LawTextChunk() {
    }

    public LawTextChunk(long documentId, String vectorId, String chunkText, int chunkOrder) {
        this.documentId = documentId;
        this.vectorId = vectorId;
        this.chunkText = chunkText;
        this.chunkOrder = chunkOrder;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(long documentId) {
        this.documentId = documentId;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public int getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(int chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
