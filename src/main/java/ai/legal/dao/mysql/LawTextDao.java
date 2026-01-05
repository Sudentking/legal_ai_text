package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.LawCodeSummary;
import ai.legal.model.LawText;
import ai.legal.model.LawTextChunk;
import ai.legal.model.LawTextChunkWithLawInfo;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL law_text 与 law_text_chunk 的访问层。
 */
public class LawTextDao {

    /**
     * 按 law_code 聚合的法律概览（用于后台管理）。
     */
    public List<LawCodeSummary> listLawSummaries(int limit) {
        List<LawCodeSummary> list = new ArrayList<>();
        String sql = "SELECT t.law_code AS law_code, MIN(t.law_title) AS law_title_sample, " +
                "COUNT(DISTINCT t.id) AS article_count, " +
                "COUNT(c.id) AS chunk_count, " +
                "SUM(CASE WHEN c.vector_id IS NOT NULL AND c.vector_id <> '' THEN 1 ELSE 0 END) AS vector_count " +
                "FROM law_text t " +
                "LEFT JOIN law_text_chunk c ON c.document_id = t.id " +
                "GROUP BY t.law_code " +
                "ORDER BY t.law_code ASC " +
                "LIMIT ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit <= 0 ? 100 : limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LawCodeSummary s = new LawCodeSummary();
                    s.setLawCode(rs.getString("law_code"));
                    s.setLawTitleSample(rs.getString("law_title_sample"));
                    s.setArticleCount(rs.getLong("article_count"));
                    s.setChunkCount(rs.getLong("chunk_count"));
                    s.setVectorCount(rs.getLong("vector_count"));
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询法律概览失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 按 law_code（支持 LIKE）分页列出条文（不读取 full_text，便于后台列表展示）。
     */
    public List<LawText> findArticlesByLawCodeLike(String lawCode, int limit, int offset) {
        List<LawText> list = new ArrayList<>();
        if (lawCode == null || lawCode.isBlank()) {
            return list;
        }
        String sql = "SELECT id, law_code, law_title, article_number, NULL AS full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE law_code LIKE ? " +
                "ORDER BY id ASC LIMIT ? OFFSET ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "%" + lawCode.trim() + "%");
            statement.setInt(2, limit <= 0 ? 50 : Math.min(limit, 500));
            statement.setInt(3, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按 law_code 查询条文列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 按 id 获取条文原文。
     */
    public LawText findById(long id) {
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE id = ? LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("按 id 查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取全部 law_text 记录。
     *
     * @return LawText 列表
     */
    public List<LawText> findAll() {
        List<LawText> list = new ArrayList<>();
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at FROM law_text";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                LawText lawText = mapRow(rs);
                list.add(lawText);
            }
        } catch (SQLException e) {
            System.err.println("查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 根据法律名称与条号查询条文（用于结构化法条查询）。
     */
    public List<LawText> findByLawNameAndArticle(String lawName, String articleKeyword, String articleKeywordAlt) {
        List<LawText> list = new ArrayList<>();
        boolean hasAlt = articleKeywordAlt != null && !articleKeywordAlt.isBlank() && !articleKeywordAlt.equals(articleKeyword);
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE (law_code LIKE ? OR law_title LIKE ?) AND " +
                (hasAlt ? "(article_number LIKE ? OR article_number LIKE ?)" : "article_number LIKE ?") +
                " ORDER BY id ASC";
        String likeName = "%" + lawName + "%";
        String likeArticle = "%" + articleKeyword + "%";
        String likeArticleAlt = hasAlt ? "%" + articleKeywordAlt + "%" : null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, likeName);
            statement.setString(2, likeName);
            statement.setString(3, likeArticle);
            if (hasAlt) {
                statement.setString(4, likeArticleAlt);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按法律名称+条号查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 根据法律名称与章节查询条文（用于结构化法条查询）。
     */
    public List<LawText> findByLawNameAndChapter(String lawName, String chapterKeyword, String chapterKeywordAlt) {
        List<LawText> list = new ArrayList<>();
        boolean hasAlt = chapterKeywordAlt != null && !chapterKeywordAlt.isBlank() && !chapterKeywordAlt.equals(chapterKeyword);
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE (law_code LIKE ? OR law_title LIKE ?) AND " +
                (hasAlt ? "(law_title LIKE ? OR law_title LIKE ?)" : "law_title LIKE ?") +
                " ORDER BY id ASC";
        String likeName = "%" + lawName + "%";
        String likeChapter = "%" + chapterKeyword + "%";
        String likeChapterAlt = hasAlt ? "%" + chapterKeywordAlt + "%" : null;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, likeName);
            statement.setString(2, likeName);
            statement.setString(3, likeChapter);
            if (hasAlt) {
                statement.setString(4, likeChapterAlt);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按法律名称+章节查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 根据法律名称 + 两个标题关键词同时命中（AND），用于“第X编 + 第X章”等组合查询。
     */
    public List<LawText> findByLawNameAndTwoTitleKeywords(String lawName,
                                                         String keyword1,
                                                         String keyword1Alt,
                                                         String keyword2,
                                                         String keyword2Alt) {
        List<LawText> list = new ArrayList<>();
        if (lawName == null || lawName.isBlank() || keyword1 == null || keyword1.isBlank() || keyword2 == null || keyword2.isBlank()) {
            return list;
        }
        boolean hasAlt1 = keyword1Alt != null && !keyword1Alt.isBlank() && !keyword1Alt.equals(keyword1);
        boolean hasAlt2 = keyword2Alt != null && !keyword2Alt.isBlank() && !keyword2Alt.equals(keyword2);

        String cond1 = hasAlt1 ? "(law_title LIKE ? OR law_title LIKE ?)" : "law_title LIKE ?";
        String cond2 = hasAlt2 ? "(law_title LIKE ? OR law_title LIKE ?)" : "law_title LIKE ?";

        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE (law_code LIKE ? OR law_title LIKE ?) AND " + cond1 + " AND " + cond2 + " ORDER BY id ASC";

        String likeName = "%" + lawName + "%";
        String like1 = "%" + keyword1 + "%";
        String like1Alt = hasAlt1 ? "%" + keyword1Alt + "%" : null;
        String like2 = "%" + keyword2 + "%";
        String like2Alt = hasAlt2 ? "%" + keyword2Alt + "%" : null;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int idx = 1;
            statement.setString(idx++, likeName);
            statement.setString(idx++, likeName);
            statement.setString(idx++, like1);
            if (hasAlt1) {
                statement.setString(idx++, like1Alt);
            }
            statement.setString(idx++, like2);
            if (hasAlt2) {
                statement.setString(idx++, like2Alt);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按法律名称+双关键词查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 根据法律名称查询全部条文。
     */
    public List<LawText> findByLawName(String lawName) {
        List<LawText> list = new ArrayList<>();
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE law_code LIKE ? OR law_title LIKE ? ORDER BY id ASC";
        String likeName = "%" + lawName + "%";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, likeName);
            statement.setString(2, likeName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按法律名称查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 按 law_code 精确匹配列出条文（不读取 full_text，适用于后台管理/质量检查）。
     */
    public List<LawText> findArticlesByLawCode(String lawCode) {
        List<LawText> list = new ArrayList<>();
        if (lawCode == null || lawCode.isBlank()) {
            return list;
        }
        String sql = "SELECT id, law_code, law_title, article_number, NULL AS full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE law_code = ? ORDER BY id ASC";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lawCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按 law_code 查询条文失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 获取某 law_code 下切片与向量的统计（chunk_count / vector_chunk_count）。
     */
    public Map<String, Long> getChunkVectorStatsByLawCode(String lawCode) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (lawCode == null || lawCode.isBlank()) {
            result.put("chunkCount", 0L);
            result.put("vectorChunkCount", 0L);
            return result;
        }
        String sql = "SELECT COUNT(c.id) AS chunk_count, " +
                "SUM(CASE WHEN c.vector_id IS NOT NULL AND c.vector_id <> '' THEN 1 ELSE 0 END) AS vector_chunk_count " +
                "FROM law_text t " +
                "LEFT JOIN law_text_chunk c ON c.document_id = t.id " +
                "WHERE t.law_code = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lawCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    result.put("chunkCount", rs.getLong("chunk_count"));
                    result.put("vectorChunkCount", rs.getLong("vector_chunk_count"));
                    return result;
                }
            }
        } catch (SQLException e) {
            System.err.println("查询切片统计失败(law_code=" + lawCode + "): " + e.getMessage());
            e.printStackTrace();
        }
        result.put("chunkCount", 0L);
        result.put("vectorChunkCount", 0L);
        return result;
    }

    /**
     * 按 law_code 精确匹配获取所有条文 id。
     */
    public List<Long> findArticleIdsByLawCode(String lawCode) {
        List<Long> list = new ArrayList<>();
        if (lawCode == null || lawCode.isBlank()) {
            return list;
        }
        String sql = "SELECT id FROM law_text WHERE law_code = ? ORDER BY id ASC";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lawCode.trim());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("按 law_code 查询条文 id 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 按法律名称 + 关键词检索条文（全文 LIKE 匹配），用于法条内关键词查询。
     */
    public List<LawText> searchByLawAndKeyword(String lawName, String keyword, int limit) {
        List<LawText> list = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return list;
        }
        String sql = "SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at " +
                "FROM law_text WHERE (law_code LIKE ? OR law_title LIKE ?) AND (full_text LIKE ? OR article_number LIKE ?) " +
                "ORDER BY id ASC LIMIT ?";
        String likeName = "%" + lawName + "%";
        String likeKw = "%" + keyword + "%";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, likeName);
            statement.setString(2, likeName);
            statement.setString(3, likeKw);
            statement.setString(4, likeKw);
            statement.setInt(5, limit <= 0 ? 10 : limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("按法律名称+关键词查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 跨法律全文关键词检索（全文/标题/法名/条号 LIKE 匹配），用于 RAG 召回失败的兜底。
     */
    public List<LawText> searchByKeywordsAcrossLaws(List<String> keywords, int limit) {
        List<LawText> list = new ArrayList<>();
        if (keywords == null || keywords.isEmpty()) {
            return list;
        }
        List<String> cleaned = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .limit(10)
                .toList();
        if (cleaned.isEmpty()) {
            return list;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, law_code, law_title, article_number, full_text, effective_date, created_at, updated_at ");
        sql.append("FROM law_text WHERE ");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(full_text LIKE ? OR law_title LIKE ? OR law_code LIKE ? OR article_number LIKE ?)");
        }
        sql.append(" ORDER BY id ASC LIMIT ?");

        int effectiveLimit = limit <= 0 ? 20 : limit;
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int idx = 1;
            for (String kw : cleaned) {
                String likeKw = "%" + kw + "%";
                statement.setString(idx++, likeKw);
                statement.setString(idx++, likeKw);
                statement.setString(idx++, likeKw);
                statement.setString(idx++, likeKw);
            }
            statement.setInt(idx, effectiveLimit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("跨法律关键词查询 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 插入一条 law_text 记录。
     *
     * @param lawText 条文
     * @return 生成的自增主键，失败返回 -1
     */
    public long insertLawText(LawText lawText) {
        String sql = "INSERT INTO law_text (law_code, law_title, article_number, full_text, effective_date) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, lawText.getLawCode());
            statement.setString(2, lawText.getLawTitle());
            statement.setString(3, lawText.getArticleNumber());
            statement.setString(4, lawText.getFullText());
            statement.setDate(5, lawText.getEffectiveDate());
            int affected = statement.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    /**
     * 删除指定条文（law_text）。
     *
     * @return 影响行数
     */
    public int deleteLawTextById(long id) {
        String sql = "DELETE FROM law_text WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("删除 law_text 失败(id=" + id + "): " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 删除指定条文的切片（law_text_chunk）。
     *
     * @return 影响行数
     */
    public int deleteChunksByDocumentId(long documentId) {
        String sql = "DELETE FROM law_text_chunk WHERE document_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, documentId);
            return statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("删除 law_text_chunk 失败(document_id=" + documentId + "): " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 获取尚未生成切片的 law_text 记录（增量导入）。
     *
     * @return LawText 列表
     */
    public List<LawText> findUnprocessed() {
        List<LawText> list = new ArrayList<>();
        String sql = "SELECT t.id, t.law_code, t.law_title, t.article_number, t.full_text, t.effective_date, t.created_at, t.updated_at " +
                "FROM law_text t " +
                "WHERE NOT EXISTS (SELECT 1 FROM law_text_chunk c WHERE c.document_id = t.id)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                LawText lawText = mapRow(rs);
                list.add(lawText);
            }
        } catch (SQLException e) {
            System.err.println("查询未处理 law_text 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 判断指定条文是否已存在切片。
     *
     * @param documentId law_text.id
     * @return 是否已有切片
     */
    public boolean hasChunks(long documentId) {
        String sql = "SELECT 1 FROM law_text_chunk WHERE document_id = ? LIMIT 1";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("检查切片存在性失败, document_id=" + documentId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 分批读取切片（包含条文标题/条号），用于“重建向量库”等运维任务。
     *
     * @param afterChunkId 上次处理到的 chunk_id（断点续跑），从 0 开始
     * @param limit        每批数量
     */
    public List<LawTextChunkWithLawInfo> findChunksWithLawInfo(long afterChunkId, int limit) {
        List<LawTextChunkWithLawInfo> list = new ArrayList<>();
        String sql = "SELECT c.id AS chunk_id, c.document_id, c.chunk_order, c.chunk_text, " +
                "t.law_title, t.article_number " +
                "FROM law_text_chunk c " +
                "JOIN law_text t ON t.id = c.document_id " +
                "WHERE c.id > ? " +
                "ORDER BY c.id ASC LIMIT ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, Math.max(0L, afterChunkId));
            statement.setInt(2, limit <= 0 ? 200 : limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LawTextChunkWithLawInfo row = new LawTextChunkWithLawInfo();
                    row.setChunkId(rs.getLong("chunk_id"));
                    row.setDocumentId(rs.getLong("document_id"));
                    row.setChunkOrder(rs.getInt("chunk_order"));
                    row.setChunkText(rs.getString("chunk_text"));
                    row.setLawTitle(rs.getString("law_title"));
                    row.setArticleNumber(rs.getString("article_number"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 law_text_chunk 失败(afterChunkId=" + afterChunkId + "): " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 更新切片的 vector_id（对应 PostgreSQL legal_embedding.id）。
     */
    public boolean updateChunkVectorId(long chunkId, String vectorId) {
        String sql = "UPDATE law_text_chunk SET vector_id = ? WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vectorId);
            statement.setLong(2, chunkId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("更新 law_text_chunk.vector_id 失败(chunk_id=" + chunkId + "): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 插入一条切片记录。
     *
     * @param chunk 切片对象，vectorId 需预先填充
     * @return 生成的自增主键，失败返回 -1
     */
    public long insertChunk(LawTextChunk chunk) {
        String sql = "INSERT INTO law_text_chunk (document_id, vector_id, chunk_text, chunk_order) VALUES (?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, chunk.getDocumentId());
            statement.setString(2, chunk.getVectorId());
            statement.setString(3, chunk.getChunkText());
            statement.setInt(4, chunk.getChunkOrder());

            int affected = statement.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 law_text_chunk 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    private Connection getConnection() throws SQLException {
        return MySqlConfig.getConnection();
    }

    private LawText mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String lawCode = rs.getString("law_code");
        String lawTitle = rs.getString("law_title");
        String articleNumber = rs.getString("article_number");
        String fullText = rs.getString("full_text");
        Date effectiveDate = rs.getDate("effective_date");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new LawText(id, lawCode, lawTitle, articleNumber, fullText, effectiveDate, createdAt, updatedAt);
    }
}
