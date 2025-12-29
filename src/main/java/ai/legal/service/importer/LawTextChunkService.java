package ai.legal.service.importer;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;
import ai.legal.model.LawTextChunk;
import ai.legal.model.LegalEmbedding;
import ai.legal.service.VectorSearchService;
import ai.legal.util.TextSplitter;
import ai.legal.service.importer.ImportPolicy;
import ai.legal.service.importer.LawTextProcessResult;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * 将 LawText 分片并写入向量库与 MySQL 切片表。
 */
public class LawTextChunkService {

    private static final int VECTOR_DIMENSION = 1536;
    private static final int DEFAULT_CHUNK_SIZE = 400;

    private final VectorSearchService vectorSearchService;
    private final LawTextDao lawTextDao;

    public LawTextChunkService(VectorSearchService vectorSearchService, LawTextDao lawTextDao) {
        this.vectorSearchService = vectorSearchService;
        this.lawTextDao = lawTextDao;
    }

    /**
     * 处理单条 LawText：分片、生成向量、写入 PostgreSQL 与 MySQL。
     *
     * @param lawText 原始条文
     */
    public LawTextProcessResult processLawText(LawText lawText, ImportPolicy policy) {
        if (lawText == null) {
            return new LawTextProcessResult(0L, true, false, 0, 0, "lawText 为空");
        }
        long documentId = lawText.getId();
        boolean skipIfProcessed = policy != null && policy.isSkipIfProcessed();
        int chunkSize = policy != null ? policy.getChunkSize() : DEFAULT_CHUNK_SIZE;

        if (skipIfProcessed && lawTextDao.hasChunks(documentId)) {
            System.out.println("已存在切片，跳过 law_text id=" + documentId);
            return new LawTextProcessResult(documentId, true, false, 0, 0, "已处理");
        }

        List<LawTextChunk> chunks = TextSplitter.splitToChunks(lawText, chunkSize);
        if (chunks.isEmpty()) {
            System.out.println("跳过空文本，ID=" + lawText.getId());
            return new LawTextProcessResult(documentId, true, false, 0, 0, "空文本");
        }

        System.out.println("开始处理 law_text id=" + lawText.getId() + "，分片数量=" + chunks.size());
        int successChunks = 0;
        int failedChunks = 0;
        for (LawTextChunk chunk : chunks) {
            try {
                double[] embeddingVector = generateEmbedding(chunk.getChunkText());
                LegalEmbedding embedding = new LegalEmbedding(
                        lawText.getId(),
                        lawText.getArticleNumber(),
                        chunk.getChunkOrder(),
                        embeddingVector,
                        chunk.getChunkText(),
                        lawText.getLawTitle()
                );
                long embeddingId = vectorSearchService.insertEmbedding(embedding);
                // 将向量表主键作为 vector_id 写入切片表
                chunk.setVectorId(String.valueOf(embeddingId));

                long chunkId = lawTextDao.insertChunk(chunk);
                System.out.println("插入分片成功: document_id=" + chunk.getDocumentId()
                        + ", chunk_order=" + chunk.getChunkOrder()
                        + ", vector_id=" + chunk.getVectorId()
                        + ", chunk_id=" + chunkId);
                successChunks++;
            } catch (SQLException e) {
                System.err.println("处理分片失败, document_id=" + chunk.getDocumentId()
                        + ", chunk_order=" + chunk.getChunkOrder() + ": " + e.getMessage());
                e.printStackTrace();
                failedChunks++;
            } catch (Exception e) {
                System.err.println("处理分片出现异常, document_id=" + chunk.getDocumentId()
                        + ", chunk_order=" + chunk.getChunkOrder() + ": " + e.getMessage());
                e.printStackTrace();
                failedChunks++;
            }
        }
        boolean success = failedChunks == 0 && successChunks > 0;
        String message = success ? "全部成功" : "存在失败";
        return new LawTextProcessResult(documentId, false, success, successChunks, failedChunks, message);
    }

    /**
     * 将字符串映射为 1536 维向量，此处用确定性生成器模拟 API 返回。
     *
     * @param text 待编码文本
     * @return 1536 维向量
     */
    private double[] generateEmbedding(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        String seed = text == null ? UUID.randomUUID().toString() : text;
        int base = seed.hashCode();
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            vector[i] = (Math.sin(base + i) + 1) * 0.5;
        }
        return vector;
    }
}
