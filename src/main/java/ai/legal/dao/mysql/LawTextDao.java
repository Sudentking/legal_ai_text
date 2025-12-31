package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.LawText;
import ai.legal.model.LawTextChunk;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL law_text 与 law_text_chunk 的访问层。
 */
public class LawTextDao {

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
