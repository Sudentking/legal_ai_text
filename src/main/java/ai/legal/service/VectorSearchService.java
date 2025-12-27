package ai.legal.service;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;

import java.sql.SQLException;
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
}
