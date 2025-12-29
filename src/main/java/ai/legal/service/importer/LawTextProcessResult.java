package ai.legal.service.importer;

/**
 * 单条 LawText 处理结果。
 */
public class LawTextProcessResult {

    private final long documentId;
    private final boolean skipped;
    private final boolean success;
    private final int successChunks;
    private final int failedChunks;
    private final String message;

    public LawTextProcessResult(long documentId, boolean skipped, boolean success,
                                int successChunks, int failedChunks, String message) {
        this.documentId = documentId;
        this.skipped = skipped;
        this.success = success;
        this.successChunks = successChunks;
        this.failedChunks = failedChunks;
        this.message = message;
    }

    public long getDocumentId() {
        return documentId;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getSuccessChunks() {
        return successChunks;
    }

    public int getFailedChunks() {
        return failedChunks;
    }

    public String getMessage() {
        return message;
    }
}
