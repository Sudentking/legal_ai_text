package ai.legal.service.importer;

/**
 * 导入策略配置，支持增量与重复跳过等控制。
 */
public class ImportPolicy {

    private final boolean incremental;
    private final boolean skipIfProcessed;
    private final int chunkSize;

    /**
     * @param incremental     是否仅处理未切片的条文
     * @param skipIfProcessed 若检测到已切片是否跳过
     * @param chunkSize       分片目标长度
     */
    public ImportPolicy(boolean incremental, boolean skipIfProcessed, int chunkSize) {
        this.incremental = incremental;
        this.skipIfProcessed = skipIfProcessed;
        this.chunkSize = chunkSize;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isSkipIfProcessed() {
        return skipIfProcessed;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * 默认策略：开启增量，跳过已处理，分片长度 400。
     */
    public static ImportPolicy defaultPolicy() {
        return new ImportPolicy(true, true, 400);
    }
}
