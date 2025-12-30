package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.QaLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 问答日志持久化，保证异常场景也有 FAIL 记录。
 */
public class QaLogDao {

    private static final String INSERT_SQL = "INSERT INTO qa_log (user_id, session_id, question, status, agent_state) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE qa_log SET answer = ?, status = ?, agent_state = ?, error_message = ? WHERE id = ?";

    public long insertInit(QaLog log) {
        if (log == null) {
            return -1L;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, log.getUserId() == null ? 0L : log.getUserId());
            ps.setString(2, log.getSessionId());
            ps.setString(3, log.getQuestion());
            ps.setString(4, log.getStatus());
            ps.setString(5, log.getAgentState());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 qa_log 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    public void updateResult(long id, String answer, String status, String agentState, String errorMessage) {
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, answer);
            ps.setString(2, status);
            ps.setString(3, agentState);
            ps.setString(4, errorMessage);
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("更新 qa_log 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
