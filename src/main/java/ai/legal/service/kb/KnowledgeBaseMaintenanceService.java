package ai.legal.service.kb;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;
import ai.legal.service.VectorSearchService;
import ai.legal.service.importer.ImportPolicy;
import ai.legal.service.importer.LawTextChunkService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库维护服务：删除/重建切片与向量（不依赖 Web/CLI）。
 *
 * <p>注意：law_id 在本项目中对应 MySQL 的 law_text.id（条文主键）。
 */
public class KnowledgeBaseMaintenanceService {

    private final LawTextDao lawTextDao;
    private final LegalEmbeddingDao embeddingDao;
    private final VectorSearchService vectorSearchService;
    private final LawTextChunkService chunkService;

    public KnowledgeBaseMaintenanceService(LawTextDao lawTextDao, LegalEmbeddingDao embeddingDao) {
        this.lawTextDao = lawTextDao;
        this.embeddingDao = embeddingDao;
        this.vectorSearchService = new VectorSearchService(embeddingDao);
        this.chunkService = new LawTextChunkService(vectorSearchService, lawTextDao);
    }

    public Map<String, Object> deleteArticle(long lawTextId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("action", "DELETE_ARTICLE");
        resp.put("lawTextId", lawTextId);

        if (lawTextId <= 0) {
            resp.put("success", false);
            resp.put("message", "invalid_lawTextId");
            return resp;
        }

        int deletedEmbeddings;
        try {
            deletedEmbeddings = embeddingDao.deleteByLawId(lawTextId);
        } catch (SQLException e) {
            resp.put("success", false);
            resp.put("message", "delete_embeddings_failed: " + e.getMessage());
            return resp;
        }

        int deletedChunks = lawTextDao.deleteChunksByDocumentId(lawTextId);
        int deletedLawTexts = lawTextDao.deleteLawTextById(lawTextId);

        resp.put("deletedEmbeddings", deletedEmbeddings);
        resp.put("deletedChunks", deletedChunks);
        resp.put("deletedLawTexts", deletedLawTexts);
        resp.put("success", deletedLawTexts > 0);
        resp.put("message", deletedLawTexts > 0 ? "ok" : "law_text_not_found_or_delete_failed");
        return resp;
    }

    public Map<String, Object> deleteLawByLawCode(String lawCode) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("action", "DELETE_LAW");
        resp.put("lawCode", lawCode);

        if (lawCode == null || lawCode.isBlank()) {
            resp.put("success", false);
            resp.put("message", "lawCode_required");
            return resp;
        }
        String code = lawCode.trim();
        List<Long> ids = lawTextDao.findArticleIdsByLawCode(code);
        resp.put("articleCount", ids.size());
        if (ids.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "no_articles_found_for_lawCode");
            return resp;
        }

        long deletedEmbeddingsTotal = 0L;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            try {
                deletedEmbeddingsTotal += embeddingDao.deleteByLawId(id);
            } catch (SQLException e) {
                errors.add("delete_embeddings_failed lawTextId=" + id + " err=" + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            // 安全起见：只要 PostgreSQL 未全部删除成功，就不删除 MySQL，避免“删了条文但向量还在”导致幽灵召回
            resp.put("success", false);
            resp.put("deletedEmbeddings", deletedEmbeddingsTotal);
            resp.put("errors", errors);
            resp.put("message", "partial_delete_embeddings_failed_abort_mysql_delete");
            return resp;
        }

        long deletedChunksTotal = 0L;
        long deletedLawTextsTotal = 0L;
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            deletedChunksTotal += lawTextDao.deleteChunksByDocumentId(id);
            deletedLawTextsTotal += lawTextDao.deleteLawTextById(id);
        }

        resp.put("deletedEmbeddings", deletedEmbeddingsTotal);
        resp.put("deletedChunks", deletedChunksTotal);
        resp.put("deletedLawTexts", deletedLawTextsTotal);
        resp.put("success", deletedLawTextsTotal > 0);
        resp.put("message", "ok");
        return resp;
    }

    public Map<String, Object> rebuildArticle(long lawTextId, int chunkSize) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("action", "REBUILD_ARTICLE");
        resp.put("lawTextId", lawTextId);
        resp.put("chunkSize", chunkSize);

        if (lawTextId <= 0) {
            resp.put("success", false);
            resp.put("message", "invalid_lawTextId");
            return resp;
        }
        if (chunkSize <= 0) {
            chunkSize = 400;
        }

        LawText lawText = lawTextDao.findById(lawTextId);
        if (lawText == null) {
            resp.put("success", false);
            resp.put("message", "law_text_not_found");
            return resp;
        }
        if (lawText.getFullText() == null || lawText.getFullText().isBlank()) {
            resp.put("success", false);
            resp.put("message", "law_text_full_text_empty");
            return resp;
        }

        int deletedEmbeddings;
        try {
            deletedEmbeddings = embeddingDao.deleteByLawId(lawTextId);
        } catch (SQLException e) {
            resp.put("success", false);
            resp.put("message", "delete_embeddings_failed: " + e.getMessage());
            return resp;
        }

        int deletedChunks = lawTextDao.deleteChunksByDocumentId(lawTextId);
        // 若删除后仍存在切片，避免重复插入
        if (lawTextDao.hasChunks(lawTextId)) {
            resp.put("success", false);
            resp.put("deletedEmbeddings", deletedEmbeddings);
            resp.put("deletedChunks", deletedChunks);
            resp.put("message", "chunk_delete_incomplete_abort_rebuild");
            return resp;
        }

        ImportPolicy policy = new ImportPolicy(false, false, chunkSize);
        var process = chunkService.processLawText(lawText, policy);
        resp.put("deletedEmbeddings", deletedEmbeddings);
        resp.put("deletedChunks", deletedChunks);
        resp.put("processed", Map.of(
                "documentId", process.getDocumentId(),
                "success", process.isSuccess(),
                "skipped", process.isSkipped(),
                "successChunks", process.getSuccessChunks(),
                "failedChunks", process.getFailedChunks(),
                "message", process.getMessage()
        ));
        resp.put("success", process.isSuccess());
        resp.put("message", process.isSuccess() ? "ok" : "rebuild_failed");
        return resp;
    }

    public Map<String, Object> rebuildLawByLawCode(String lawCode, int chunkSize) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("action", "REBUILD_LAW");
        resp.put("lawCode", lawCode);
        resp.put("chunkSize", chunkSize);

        if (lawCode == null || lawCode.isBlank()) {
            resp.put("success", false);
            resp.put("message", "lawCode_required");
            return resp;
        }
        if (chunkSize <= 0) {
            chunkSize = 400;
        }
        String code = lawCode.trim();
        List<Long> ids = lawTextDao.findArticleIdsByLawCode(code);
        resp.put("articleCount", ids.size());
        if (ids.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "no_articles_found_for_lawCode");
            return resp;
        }

        List<Map<String, Object>> perArticle = new ArrayList<>();
        int ok = 0;
        int fail = 0;
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            Map<String, Object> r = rebuildArticle(id, chunkSize);
            perArticle.add(r);
            Object success = r.get("success");
            if (Boolean.TRUE.equals(success)) {
                ok++;
            } else {
                fail++;
            }
        }
        resp.put("successCount", ok);
        resp.put("failCount", fail);
        resp.put("results", perArticle);
        resp.put("success", fail == 0 && ok > 0);
        resp.put("message", fail == 0 ? "ok" : "partial_failed");
        return resp;
    }
}

