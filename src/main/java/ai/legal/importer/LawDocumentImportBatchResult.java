package ai.legal.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量导入结果（面向后续 Web/接口调用）。
 */
public class LawDocumentImportBatchResult {

    private int totalFiles;
    private int successFiles;
    private int failedFiles;
    private int skippedFiles;
    private int parsedLawTexts;
    private int insertedLawTexts;
    private int failedLawTexts;
    private long elapsedMs;
    private final List<LawDocumentImportFileResult> fileResults = new ArrayList<>();

    public void addFileResult(LawDocumentImportFileResult r) {
        if (r == null) {
            return;
        }
        fileResults.add(r);
        totalFiles++;
        if (r.isSuccess()) {
            successFiles++;
        } else if (r.isSkipped()) {
            skippedFiles++;
        } else {
            failedFiles++;
        }
        parsedLawTexts += r.getParsedLawTexts();
        insertedLawTexts += r.getInsertedLawTexts();
        failedLawTexts += r.getFailedLawTexts();
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getSuccessFiles() {
        return successFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public int getParsedLawTexts() {
        return parsedLawTexts;
    }

    public int getInsertedLawTexts() {
        return insertedLawTexts;
    }

    public int getFailedLawTexts() {
        return failedLawTexts;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public List<LawDocumentImportFileResult> getFileResults() {
        return Collections.unmodifiableList(fileResults);
    }
}

