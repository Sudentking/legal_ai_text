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
            "(session_id, user_query, agent_state, intent_type, decision_reason) VALUES (?, ?, ?, ?, ?)";

    public void insertHistory(AgentTaskHistory history) {
        if (history == null) {
            return;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
            ps.setString(1, history.getSessionId());
            ps.setString(2, history.getUserQuery());
            ps.setString(3, history.getAgentState());
            ps.setString(4, history.getIntentType());
            ps.setString(5, history.getDecisionReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("插入 agent_task_history 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<AgentTaskHistory> findRecentBySession(String sessionId, int limit) {
        List<AgentTaskHistory> list = new ArrayList<>();
        String sql = "SELECT id, session_id, user_query, agent_state, intent_type, decision_reason, created_at " +
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
                    h.setAgentState(rs.getString("agent_state"));
                    h.setIntentType(rs.getString("intent_type"));
                    h.setDecisionReason(rs.getString("decision_reason"));
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
