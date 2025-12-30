package ai.legal.service;

import ai.legal.dao.mysql.AgentTaskHistoryDao;
import ai.legal.model.AgentTaskHistory;

import java.util.List;

/**
 * 读取任务历史用于决策，而非仅日志。
 */
public class AgentTaskHistoryService {

    private final AgentTaskHistoryDao dao;

    public AgentTaskHistoryService(AgentTaskHistoryDao dao) {
        this.dao = dao;
    }

    /**
     * 获取指定 session 最近 N 条历史。
     */
    public List<AgentTaskHistory> findRecent(String sessionId, int limit) {
        return dao.findRecentBySession(sessionId, limit);
    }

    /**
     * 判断最近是否有相同 query 的 RAG 失败记录。
     */
    public boolean recentRagFailedSameQuery(String sessionId, String userQuery) {
        List<AgentTaskHistory> recent = dao.findRecentBySession(sessionId, 3);
        if (recent == null || recent.isEmpty() || userQuery == null) {
            return false;
        }
        String q = userQuery.trim();
        for (AgentTaskHistory h : recent) {
            if (h.getUserQuery() != null && h.getUserQuery().trim().equalsIgnoreCase(q)) {
                if ("fail".equalsIgnoreCase(h.getResultStatus())
                        && "RAG".equalsIgnoreCase(h.getStrategyUsed())) {
                    return true;
                }
            }
        }
        return false;
    }
}
