package ai.legal.importer;

/**
 * 单个文件的导入结果（面向后续 Web/接口调用）。
 */
public class LawDocumentImportFileResult {

    public enum Status {
        SUCCESS,
        FAIL,
        SKIPPED
    }

    private String path;
    private Status status = Status.FAIL;
    private String message;
    private int parsedLawTexts;
    private int insertedLawTexts;
    private int failedLawTexts;
    private long elapsedMs;

    public LawDocumentImportFileResult() {
    }

    public static LawDocumentImportFileResult success(String path) {
        LawDocumentImportFileResult r = new LawDocumentImportFileResult();
        r.path = path;
        r.status = Status.SUCCESS;
        return r;
    }

    public static LawDocumentImportFileResult fail(String path, String message) {
        LawDocumentImportFileResult r = new LawDocumentImportFileResult();
        r.path = path;
        r.status = Status.FAIL;
        r.message = message;
        return r;
    }

    public static LawDocumentImportFileResult skipped(String path, String message) {
        LawDocumentImportFileResult r = new LawDocumentImportFileResult();
        r.path = path;
        r.status = Status.SKIPPED;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getParsedLawTexts() {
        return parsedLawTexts;
    }

    public void setParsedLawTexts(int parsedLawTexts) {
        this.parsedLawTexts = parsedLawTexts;
    }

    public int getInsertedLawTexts() {
        return insertedLawTexts;
    }

    public void setInsertedLawTexts(int insertedLawTexts) {
        this.insertedLawTexts = insertedLawTexts;
    }

    public int getFailedLawTexts() {
        return failedLawTexts;
    }

    public void setFailedLawTexts(int failedLawTexts) {
        this.failedLawTexts = failedLawTexts;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}

