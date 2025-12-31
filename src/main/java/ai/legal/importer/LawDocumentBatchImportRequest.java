package ai.legal.importer;

import ai.legal.service.importer.ImportPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量导入请求（面向后续 Web/接口调用）。
 */
public class LawDocumentBatchImportRequest {

    private final List<String> paths = new ArrayList<>();
    private boolean recursive = false;
    private int chunkSize = 400;
    private boolean skipIfProcessed = true;

    public LawDocumentBatchImportRequest() {
    }

    public LawDocumentBatchImportRequest(List<String> paths) {
        if (paths != null) {
            this.paths.addAll(paths);
        }
    }

    public List<String> getPaths() {
        return Collections.unmodifiableList(paths);
    }

    public void addPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        this.paths.add(path);
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public boolean isSkipIfProcessed() {
        return skipIfProcessed;
    }

    public void setSkipIfProcessed(boolean skipIfProcessed) {
        this.skipIfProcessed = skipIfProcessed;
    }

    public ImportPolicy toPolicy() {
        // 文件导入场景下 incremental 对本次逻辑无影响，保持与默认策略一致
        return new ImportPolicy(true, skipIfProcessed, chunkSize);
    }
}

