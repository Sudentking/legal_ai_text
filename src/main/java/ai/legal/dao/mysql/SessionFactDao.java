package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.SessionFact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * session_fact 表访问层：会话事实记忆（用于 Agent 决策与避免重复追问）。
 */
public class SessionFactDao {

    private static volatile boolean DISABLED = false;
    private static volatile boolean DISABLE_WARNED = false;

    public boolean upsert(String sessionId, String factKey, String factValue, String sourceType, String sourceMessage) {
        if (DISABLED) {
            return false;
        }
        if (sessionId == null || sessionId.isBlank() || factKey == null || factKey.isBlank()) {
            return false;
        }
        String sql = "INSERT INTO session_fact (session_id, fact_key, fact_value, source_type, source_message) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE fact_value = VALUES(fact_value), source_type = VALUES(source_type), " +
                "source_message = VALUES(source_message), updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId.trim());
            ps.setString(2, factKey.trim());
            ps.setString(3, factValue);
            ps.setString(4, sourceType);
            ps.setString(5, sourceMessage);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (disableIfMissingTable(e)) {
                return false;
            }
            System.err.println("写入 session_fact 失败: " + e.getMessage());
            return false;
        }
    }

    public List<SessionFact> findBySession(String sessionId, int limit) {
        List<SessionFact> list = new ArrayList<>();
        if (DISABLED) {
            return list;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return list;
        }
        String sql = "SELECT id, session_id, fact_key, fact_value, source_type, source_message, created_at, updated_at " +
                "FROM session_fact WHERE session_id = ? ORDER BY updated_at DESC LIMIT ?";
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId.trim());
            ps.setInt(2, limit <= 0 ? 50 : limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            if (disableIfMissingTable(e)) {
                return list;
            }
            System.err.println("查询 session_fact 失败: " + e.getMessage());
        }
        return list;
    }

    public int deleteBySession(String sessionId) {
        if (DISABLED) {
            return 0;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        String sql = "DELETE FROM session_fact WHERE session_id = ?";
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId.trim());
            return ps.executeUpdate();
        } catch (SQLException e) {
            if (disableIfMissingTable(e)) {
                return 0;
            }
            System.err.println("删除 session_fact 失败: " + e.getMessage());
            return 0;
        }
    }

    private boolean disableIfMissingTable(SQLException e) {
        if (e == null) {
            return false;
        }
        String state = e.getSQLState();
        if ("42S02".equals(state)) { // MySQL: table doesn't exist
            DISABLED = true;
            warnDisabledOnce(e);
            return true;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("session_fact") && (lower.contains("doesn't exist") || lower.contains("does not exist") || lower.contains("unknown table"))) {
            DISABLED = true;
            warnDisabledOnce(e);
            return true;
        }
        return false;
    }

    private void warnDisabledOnce(SQLException e) {
        if (DISABLE_WARNED) {
            return;
        }
        DISABLE_WARNED = true;
        System.err.println("检测到 session_fact 表不可用，已自动停用会话事实记忆（不影响主流程）。");
        System.err.println("请执行：sql/mysql_session_fact_schema.sql（在 mysql.url 指向的库中）。");
        if (e.getMessage() != null) {
            System.err.println("原因：" + e.getMessage());
        }
    }

    private SessionFact mapRow(ResultSet rs) throws SQLException {
        SessionFact f = new SessionFact();
        f.setId(rs.getLong("id"));
        f.setSessionId(rs.getString("session_id"));
        f.setFactKey(rs.getString("fact_key"));
        f.setFactValue(rs.getString("fact_value"));
        f.setSourceType(rs.getString("source_type"));
        f.setSourceMessage(rs.getString("source_message"));
        f.setCreatedAt(rs.getTimestamp("created_at"));
        f.setUpdatedAt(rs.getTimestamp("updated_at"));
        return f;
    }
}
