package ai.legal.dao.mysql;

import ai.legal.model.LawText;
import ai.legal.model.LawTextChunk;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
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

    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/legal_dev?useSSL=false&serverTimezone=UTC";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "root123456";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 MySQL JDBC 驱动", e);
        }
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
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
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
