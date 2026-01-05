package ai.legal.dao;

import ai.legal.config.DatabaseConfig;
import ai.legal.model.LegalEmbedding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责 legal_embedding 表的插入与向量检索。
 */
public class LegalEmbeddingDao {

    private static final int VECTOR_DIMENSION = 1536;

    /**
     * 清空向量表（用于重建向量库）。
     *
     * <p>优先使用 TRUNCATE（更快，且可重置自增序列）；失败时退化为 DELETE。
     */
    public void clearAll() throws SQLException {
        String truncate = "TRUNCATE TABLE legal_embedding RESTART IDENTITY";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(truncate)) {
            statement.execute();
            return;
        } catch (SQLException truncateError) {
            String del = "DELETE FROM legal_embedding";
            try (Connection connection = DatabaseConfig.getConnection();
                 PreparedStatement statement = connection.prepareStatement(del)) {
                statement.executeUpdate();
            }
        }
    }

    /**
     * 按 law_id 删除向量记录（law_id 对应 MySQL law_text.id）。
     *
     * @return 删除行数
     */
    public int deleteByLawId(long lawId) throws SQLException {
        String sql = "DELETE FROM legal_embedding WHERE law_id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lawId);
            return statement.executeUpdate();
        }
    }

    public long insert(LegalEmbedding embedding) throws SQLException {
        if (embedding == null) {
            throw new IllegalArgumentException("待插入对象不能为空");
        }
        validateVector(embedding.getEmbedding());

        // 使用 RETURNING 直接获取生成的主键
        String sql = "INSERT INTO legal_embedding (law_id, article_no, chunk_index, embedding, content, source) " +
                "VALUES (?, ?, ?, ?::vector, ?, ?) RETURNING id";

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // 填充参数，均使用 PreparedStatement 防止 SQL 注入
            statement.setLong(1, embedding.getLawId());
            statement.setString(2, embedding.getArticleNo());
            statement.setInt(3, embedding.getChunkIndex());
            statement.setString(4, toVectorLiteral(embedding.getEmbedding()));
            statement.setString(5, embedding.getContent());
            statement.setString(6, embedding.getSource());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    embedding.setId(id);
                    return id;
                } else {
                    throw new SQLException("插入未返回主键");
                }
            }
        }
    }

    public List<LegalEmbedding> searchSimilar(double[] queryVector, int topK) throws SQLException {
        validateVector(queryVector);
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须为正整数");
        }

        // 基于 pgvector 的 L2 距离运算符 <-> 做相似度排序
        String sql = "SELECT id, law_id, article_no, chunk_index, embedding, content, source, created_at " +
                "FROM legal_embedding " +
                "ORDER BY embedding <-> ?::vector " +
                "LIMIT ?";

        List<LegalEmbedding> results = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // 传入查询向量与限制数量
            statement.setString(1, toVectorLiteral(queryVector));
            statement.setInt(2, topK);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    // 将查询结果映射为模型对象
                    LegalEmbedding embedding = new LegalEmbedding(
                            rs.getLong("law_id"),
                            rs.getString("article_no"),
                            rs.getInt("chunk_index"),
                            parseVector(rs.getString("embedding")),
                            rs.getString("content"),
                            rs.getString("source")
                    );
                    embedding.setId(rs.getLong("id"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    embedding.setCreatedAt(createdAt);
                    results.add(embedding);
                }
            }
        }
        return results;
    }

    // 校验向量非空且维度正确
    private void validateVector(double[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("向量不能为空");
        }
        if (vector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("向量维度必须为 " + VECTOR_DIMENSION);
        }
    }

    // 将 double 数组拼接成 pgvector 接受的字符串形式
    private String toVectorLiteral(double[] vector) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            builder.append(vector[i]);
            if (i < vector.length - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    // 将数据库中的 "[...]" 字符串解析回 double 数组
    private double[] parseVector(String text) {
        if (text == null || text.isEmpty()) {
            return new double[0];
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return new double[0];
        }
        String[] parts = trimmed.split(",");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        return values;
    }
}
