package ai.legal.service.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入的整体结果统计。
 */
public class ImportResult {

    private int total;
    private int success;
    private int failed;
    private int skipped;
    private long elapsedMs;
    private final List<Long> failedIds = new ArrayList<>();
    private final List<Long> skippedIds = new ArrayList<>();

    public void add(LawTextProcessResult result) {
        total++;
        if (result.isSkipped()) {
            skipped++;
            skippedIds.add(result.getDocumentId());
            return;
        }
        if (result.isSuccess()) {
            success++;
        } else {
            failed++;
            failedIds.add(result.getDocumentId());
        }
    }

    public int getTotal() {
        return total;
    }

    public int getSuccess() {
        return success;
    }

    public int getFailed() {
        return failed;
    }

    public int getSkipped() {
        return skipped;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public List<Long> getFailedIds() {
        return failedIds;
    }

    public List<Long> getSkippedIds() {
        return skippedIds;
    }
}
