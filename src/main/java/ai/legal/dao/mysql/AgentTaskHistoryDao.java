package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.AgentTaskHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * agent_task_history 表的持久化访问层。
 */
public class AgentTaskHistoryDao {

    private static final String INSERT_SQL = "INSERT INTO agent_task_history " +
            "(session_id, user_query, intent_type, strategy_used, result_status, fail_reason) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE agent_task_history SET result_status = ?, fail_reason = ? WHERE id = ?";

    /**
     * 两阶段日志：先写入 PENDING，后续根据执行结果更新 SUCCESS/FAIL。
     *
     * @return 自增主键，失败返回 -1
     */
    public long insertHistory(AgentTaskHistory history) {
        if (history == null) {
            return -1L;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, history.getSessionId());
            ps.setString(2, history.getUserQuery());
            ps.setString(3, history.getIntentType());
            ps.setString(4, history.getStrategyUsed());
            ps.setString(5, history.getResultStatus());
            ps.setString(6, history.getFailReason());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    history.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 agent_task_history 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    public void updateResult(long id, String resultStatus, String failReason) {
        if (id <= 0) {
            return;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, resultStatus);
            ps.setString(2, failReason);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("更新 agent_task_history 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<AgentTaskHistory> findRecentBySession(String sessionId, int limit) {
        List<AgentTaskHistory> list = new ArrayList<>();
        String sql = "SELECT id, session_id, user_query, intent_type, strategy_used, result_status, fail_reason, created_at " +
                "FROM agent_task_history WHERE session_id = ? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit <= 0 ? 5 : limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AgentTaskHistory h = new AgentTaskHistory();
                    h.setId(rs.getLong("id"));
                    h.setSessionId(rs.getString("session_id"));
                    h.setUserQuery(rs.getString("user_query"));
                    h.setIntentType(rs.getString("intent_type"));
                    h.setStrategyUsed(rs.getString("strategy_used"));
                    h.setResultStatus(rs.getString("result_status"));
                    h.setFailReason(rs.getString("fail_reason"));
                    h.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(h);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 agent_task_history 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public List<AgentTaskHistory> findRecent(int limit) {
        List<AgentTaskHistory> list = new ArrayList<>();
        String sql = "SELECT id, session_id, user_query, intent_type, strategy_used, result_status, fail_reason, created_at " +
                "FROM agent_task_history ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit <= 0 ? 50 : limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AgentTaskHistory h = new AgentTaskHistory();
                    h.setId(rs.getLong("id"));
                    h.setSessionId(rs.getString("session_id"));
                    h.setUserQuery(rs.getString("user_query"));
                    h.setIntentType(rs.getString("intent_type"));
                    h.setStrategyUsed(rs.getString("strategy_used"));
                    h.setResultStatus(rs.getString("result_status"));
                    h.setFailReason(rs.getString("fail_reason"));
                    h.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(h);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 agent_task_history 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}
