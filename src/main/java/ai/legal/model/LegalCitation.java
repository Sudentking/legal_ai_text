package ai.legal.model;

/**
 * 法律条文引用定位信息。
 */
public class LegalCitation {
    private final long lawId;
    private final String articleNo;
    private final Integer clauseIndex;
    private final int chunkIndex;

    public LegalCitation(long lawId, String articleNo, Integer clauseIndex, int chunkIndex) {
        this.lawId = lawId;
        this.articleNo = articleNo;
        this.clauseIndex = clauseIndex;
        this.chunkIndex = chunkIndex;
    }

    public long getLawId() {
        return lawId;
    }

    public String getArticleNo() {
        return articleNo;
    }

    public Integer getClauseIndex() {
        return clauseIndex;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }
}
