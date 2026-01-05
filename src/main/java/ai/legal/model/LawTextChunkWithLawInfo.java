package ai.legal.model;

/**
 * 用于“重建向量库”等运维任务的切片视图：切片信息 + 对应条文的关键信息。
 */
public class LawTextChunkWithLawInfo {
    private long chunkId;
    private long documentId;
    private int chunkOrder;
    private String chunkText;
    private String lawTitle;
    private String articleNumber;

    public long getChunkId() {
        return chunkId;
    }

    public void setChunkId(long chunkId) {
        this.chunkId = chunkId;
    }

    public long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(long documentId) {
        this.documentId = documentId;
    }

    public int getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(int chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public String getLawTitle() {
        return lawTitle;
    }

    public void setLawTitle(String lawTitle) {
        this.lawTitle = lawTitle;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
    }
}

