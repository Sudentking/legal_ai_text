package ai.legal.model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * 对应 MySQL legal_text 表的条文记录。
 */
public class LawText {
    // 条文ID，自增主键
    private long id;
    // 法律编号
    private String lawCode;
    // 法律标题
    private String lawTitle;
    // 条文编号
    private String articleNumber;
    // 完整条文内容
    private String fullText;
    // 生效日期
    private Date effectiveDate;
    // 创建时间
    private Timestamp createdAt;
    // 更新时间
    private Timestamp updatedAt;

    public LawText() {
    }

    public LawText(long id, String lawCode, String lawTitle, String articleNumber, String fullText,
                   Date effectiveDate, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.lawCode = lawCode;
        this.lawTitle = lawTitle;
        this.articleNumber = articleNumber;
        this.fullText = fullText;
        this.effectiveDate = effectiveDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLawCode() {
        return lawCode;
    }

    public void setLawCode(String lawCode) {
        this.lawCode = lawCode;
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

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(Date effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
