package ai.legal.service;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * 封装向量插入与相似度查询的业务逻辑。
 */
public class VectorSearchService {

    private final LegalEmbeddingDao dao;

    // 通过构造函数注入 DAO，保持职责单一
    public VectorSearchService(LegalEmbeddingDao dao) {
        this.dao = dao;
    }

    // 插入一条向量记录
    public long insertEmbedding(LegalEmbedding embedding) throws SQLException {
        return dao.insert(embedding);
    }

    // 按相似度获取 Top-K 记录
    public List<LegalEmbedding> searchSimilar(double[] queryVector, int topK) throws SQLException {
        return dao.searchSimilar(queryVector, topK);
    }

    /**
     * 基于 pgvector 的 L2 距离获取 Top-K 相似结果。
     *
     * @param queryVector 查询向量，长度必须为 1536
     * @param k           返回数量
     * @return 相似的 LegalEmbedding 列表
     */
    public List<LegalEmbedding> searchTopK(double[] queryVector, int k) {
        try {
            return dao.searchSimilar(queryVector, k);
        } catch (SQLException e) {
            System.err.println("向量相似度查询失败: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
