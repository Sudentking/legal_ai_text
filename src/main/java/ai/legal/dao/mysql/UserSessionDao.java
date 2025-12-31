package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.UserSession;
import ai.legal.model.UserSessionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * 登录会话表（user_session）访问层。
 */
public class UserSessionDao {

    private static final String INSERT_SQL = "INSERT INTO user_session (session_id, user_id, status, expires_at) VALUES (?, ?, ?, ?)";
    private static final String FIND_ACTIVE_SQL = "SELECT id, session_id, user_id, status, expires_at, created_at " +
            "FROM user_session WHERE session_id = ? AND status = 'ACTIVE' LIMIT 1";
    private static final String REVOKE_SQL = "UPDATE user_session SET status = 'REVOKED' WHERE session_id = ? AND status = 'ACTIVE'";

    public long insert(UserSession session) {
        if (session == null || session.getSessionId() == null || session.getSessionId().isBlank() || session.getUserId() == null) {
            return -1L;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, session.getSessionId());
            ps.setLong(2, session.getUserId());
            ps.setString(3, session.getStatus() == null ? UserSessionStatus.ACTIVE.name() : session.getStatus().name());
            ps.setTimestamp(4, session.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    session.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 user_session 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    public UserSession findActiveBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(FIND_ACTIVE_SQL)) {
            ps.setString(1, sessionId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 user_session 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public boolean revoke(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(REVOKE_SQL)) {
            ps.setString(1, sessionId.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("撤销 user_session 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private UserSession mapRow(ResultSet rs) throws SQLException {
        UserSession s = new UserSession();
        s.setId(rs.getLong("id"));
        s.setSessionId(rs.getString("session_id"));
        s.setUserId(rs.getLong("user_id"));
        s.setStatus(parseStatus(rs.getString("status")));
        s.setExpiresAt(rs.getTimestamp("expires_at"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        s.setCreatedAt(createdAt);
        return s;
    }

    private UserSessionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserSessionStatus.ACTIVE;
        }
        try {
            return UserSessionStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            return UserSessionStatus.ACTIVE;
        }
    }
}

