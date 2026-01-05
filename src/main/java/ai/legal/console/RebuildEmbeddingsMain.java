package ai.legal.console;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawTextChunkWithLawInfo;
import ai.legal.model.LegalEmbedding;
import ai.legal.service.VectorSearchService;
import ai.legal.util.EmbeddingUtil;

import java.util.List;

/**
 * 从 MySQL 的 law_text_chunk 重建 PostgreSQL 的 legal_embedding（支持断点续跑）。
 *
 * <p>用法示例：
 * <pre>
 *   java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.RebuildEmbeddingsMain --truncate --batchSize 300
 *   java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.RebuildEmbeddingsMain --fromChunkId 120000 --batchSize 300
 * </pre>
 */
public class RebuildEmbeddingsMain {

    public static void main(String[] args) {
        Options options = Options.parse(args);
        System.out.println("RebuildEmbeddings 启动：embedding.mode=" + EmbeddingUtil.getMode().getCode()
                + " batchSize=" + options.batchSize
                + " fromChunkId=" + options.fromChunkId
                + " truncate=" + options.truncate
                + " dryRun=" + options.dryRun);

        LawTextDao lawTextDao = new LawTextDao();
        LegalEmbeddingDao embeddingDao = new LegalEmbeddingDao();
        VectorSearchService vectorSearchService = new VectorSearchService(embeddingDao);

        if (options.truncate) {
            if (options.dryRun) {
                System.out.println("[dry-run] 跳过清空 legal_embedding");
            } else {
                try {
                    System.out.println("开始清空 PostgreSQL legal_embedding ...");
                    embeddingDao.clearAll();
                    System.out.println("已清空 legal_embedding");
                } catch (Exception e) {
                    System.err.println("清空 legal_embedding 失败: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
        }

        long after = Math.max(0L, options.fromChunkId);
        long processed = 0L;
        long succeeded = 0L;
        long failed = 0L;

        while (true) {
            List<LawTextChunkWithLawInfo> batch = lawTextDao.findChunksWithLawInfo(after, options.batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (LawTextChunkWithLawInfo row : batch) {
                processed++;
                after = Math.max(after, row.getChunkId());
                if (options.maxChunks > 0 && processed > options.maxChunks) {
                    break;
                }
                try {
                    if (options.dryRun) {
                        succeeded++;
                        continue;
                    }
                    double[] vector = EmbeddingUtil.embed(row.getChunkText());
                    LegalEmbedding embedding = new LegalEmbedding(
                            row.getDocumentId(),
                            row.getArticleNumber(),
                            row.getChunkOrder(),
                            vector,
                            row.getChunkText(),
                            row.getLawTitle()
                    );
                    long embeddingId = vectorSearchService.insertEmbedding(embedding);
                    boolean updated = lawTextDao.updateChunkVectorId(row.getChunkId(), String.valueOf(embeddingId));
                    if (!updated) {
                        System.err.println("警告：已插入 embedding id=" + embeddingId
                                + " 但更新 law_text_chunk.vector_id 失败 chunk_id=" + row.getChunkId());
                    }
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    System.err.println("重建失败 chunk_id=" + row.getChunkId()
                            + " document_id=" + row.getDocumentId()
                            + " chunk_order=" + row.getChunkOrder()
                            + " err=" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
            if (options.maxChunks > 0 && processed > options.maxChunks) {
                break;
            }
            if (processed % 2000 == 0) {
                System.out.println("进度：processed=" + processed + " succeeded=" + succeeded + " failed=" + failed + " lastChunkId=" + after);
            }
        }

        System.out.println("RebuildEmbeddings 完成：processed=" + processed
                + " succeeded=" + succeeded
                + " failed=" + failed
                + " lastChunkId=" + after);
        if (failed > 0) {
            System.out.println("建议：可用 --fromChunkId " + after + " 断点续跑；或先排查失败原因再重跑。");
        }
    }

    private static final class Options {
        private boolean truncate;
        private boolean dryRun;
        private int batchSize = 200;
        private long fromChunkId = 0L;
        private long maxChunks = -1L;

        private static Options parse(String[] args) {
            Options o = new Options();
            if (args == null || args.length == 0) {
                return o;
            }
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a == null || a.isBlank()) {
                    continue;
                }
                if ("--truncate".equalsIgnoreCase(a)) {
                    o.truncate = true;
                    continue;
                }
                if ("--dryRun".equalsIgnoreCase(a) || "--dry-run".equalsIgnoreCase(a)) {
                    o.dryRun = true;
                    continue;
                }
                if ("--batchSize".equalsIgnoreCase(a) || "--batch-size".equalsIgnoreCase(a)) {
                    if (i + 1 < args.length) {
                        o.batchSize = parseInt(args[++i], 200);
                    }
                    continue;
                }
                if ("--fromChunkId".equalsIgnoreCase(a) || "--from-chunk-id".equalsIgnoreCase(a)) {
                    if (i + 1 < args.length) {
                        o.fromChunkId = parseLong(args[++i], 0L);
                    }
                    continue;
                }
                if ("--maxChunks".equalsIgnoreCase(a) || "--max-chunks".equalsIgnoreCase(a)) {
                    if (i + 1 < args.length) {
                        o.maxChunks = parseLong(args[++i], -1L);
                    }
                }
            }
            return o;
        }

        private static int parseInt(String raw, int defaultValue) {
            try {
                return Integer.parseInt(raw);
            } catch (Exception e) {
                return defaultValue;
            }
        }

        private static long parseLong(String raw, long defaultValue) {
            try {
                return Long.parseLong(raw);
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }
}

