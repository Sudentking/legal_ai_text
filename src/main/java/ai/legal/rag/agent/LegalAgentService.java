package ai.legal.rag.agent;

import ai.legal.dao.mysql.AgentTaskHistoryDao;
import ai.legal.dao.mysql.QaLogDao;
import ai.legal.model.AgentTaskHistory;
import ai.legal.model.QaLog;
import ai.legal.rag.intent.LegalIntentClassifier;
import ai.legal.rag.intent.LegalIntentResult;
import ai.legal.rag.intent.LegalIntentType;
import ai.legal.rag.prompt.ClarificationPromptBuilder;
import ai.legal.rag.service.FactSufficiencyEvaluator;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.rag.service.StructuredLawQueryService;
import ai.legal.service.AgentTaskHistoryService;

import java.util.List;

/**
 * 法律智能 Agent：意图识别 → 决策 → RAG → LLM。
 * 集成 FSM 与两阶段日志（PENDING → SUCCESS/FAIL）。
 */
public class LegalAgentService {

    private final LegalIntentClassifier intentClassifier;
    private final LegalRagQaService ragQaService;
    private final StructuredLawQueryService structuredLawQueryService;
    private final FactSufficiencyEvaluator factSufficiencyEvaluator = new FactSufficiencyEvaluator();
    private final LlmClient llmClient;
    private final AgentTaskHistoryDao historyDao = new AgentTaskHistoryDao();
    private final AgentTaskHistoryService historyService = new AgentTaskHistoryService(historyDao);
    private final QaLogDao qaLogDao = new QaLogDao();
    private final AgentStateMachine fsm = new AgentStateMachine();

    private String sessionId = "default-session";
    private Long currentUserId = 1L;
    private AgentState state = AgentState.INIT;
    private String lastSolutionSummary = "";

    public LegalAgentService(LegalIntentClassifier intentClassifier,
                             LegalRagQaService ragQaService,
                             StructuredLawQueryService structuredLawQueryService,
                             LlmClient llmClient) {
        this.intentClassifier = intentClassifier;
        this.ragQaService = ragQaService;
        this.structuredLawQueryService = structuredLawQueryService;
        this.llmClient = llmClient;
    }

    public String answer(String userQuestion) {
        return answer(userQuestion, "");
    }

    public String answer(String userQuestion, String historyFacts) {
        return answer(this.currentUserId, this.sessionId, userQuestion, historyFacts);
    }

    public String answer(String sessionId, String userQuestion, String historyFacts) {
        return answer(this.currentUserId, sessionId, userQuestion, historyFacts);
    }

    /**
     * 带 userId + sessionId 的主入口。
     */
    public String answer(Long userId, String sessionId, String userQuestion, String historyFacts) {
        if (sessionId != null && !sessionId.isBlank()) {
            this.sessionId = sessionId;
        }
        if (userId != null) {
            this.currentUserId = userId;
        }

        long logId = qaLogDao.insertInit(new QaLog(this.currentUserId, this.sessionId, userQuestion, "PENDING", state.name()));
        try {
            List<AgentTaskHistory> recent = historyService.findRecent(this.sessionId, 5);

            // 历史决策：避免重复追问/重复失败
            if (historyService.recentRagFailedSameQuery(this.sessionId, userQuestion)) {
                state = AgentState.FINISHED;
                logTaskHistory(userQuestion, "UNKNOWN", "STRUCTURED_SQL", "fail", "上次相同问题 RAG 失败，跳过 RAG");
                String msg = "上次相同问题向量检索失败，本次未重复检索，请尝试明确条号或更换问法。";
                qaLogDao.updateResult(logId, msg, "FAIL", state.name(), "重复失败的向量检索已跳过");
                return msg;
            }
            if (isRepeatedQuery(userQuestion, recent)) {
                state = AgentState.SOLUTION_GENERATED;
                logTaskHistory(userQuestion, "REPEAT", "RAG", "success", null);
                String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }
            if (state == AgentState.NEED_FOLLOW_UP) {
                String result = buildFollowUpReminder();
                logTaskHistory(userQuestion, null, "FOLLOW_UP", "success", null);
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }

            // 状态机重置
            state = AgentState.INIT;
            fsm.reset();
            logState(userQuestion, null, "初始化");

            // 结构化命中直接返回
            String structured = structuredLawQueryService.answerIfStructured(userQuestion);
            if (structured != null) {
                state = AgentState.FINISHED;
                fsm.next(null, true, true);
                logTaskHistory(userQuestion, "LAW_TEXT_QUERY", "STRUCTURED_SQL", "success", null);
                qaLogDao.updateResult(logId, structured, "SUCCESS", state.name(), null);
                return structured;
            }

            LegalIntentResult intentResult = intentClassifier.classify(userQuestion);
            state = AgentState.INTENT_RECOGNIZED;
            logState(userQuestion, intentResult, "意图识别");
            boolean factsSufficient = factSufficiencyEvaluator.isLoanFactsSufficient(userQuestion);
            boolean hasLawLocator = intentResult.getType() == LegalIntentType.LAW_TEXT_QUERY;
            FsmAgentState next = fsm.next(intentResult.getType(), hasLawLocator, factsSufficient);

            switch (next) {
                case DIRECT_LAW_QUERY -> {
                    state = AgentState.FINISHED;
                    String msg = "未在数据库找到对应条文";
                    logTaskHistory(userQuestion, intentResult.getType().name(), "STRUCTURED_SQL", "fail", msg);
                    qaLogDao.updateResult(logId, msg, "SUCCESS", state.name(), null);
                    return msg;
                }
                case FACT_CHECK -> {
                    logTaskHistory(userQuestion, intentResult.getType().name(), "RAG", "success", null);
                    return generateSolutionThenFollowUp(userQuestion, intentResult.getReason(), historyFacts, logId);
                }
                case RAG_RETRIEVAL, LLM_ANSWER, FINISHED -> {
                    state = AgentState.FINISHED;
                    logTaskHistory(userQuestion, intentResult.getType().name(), "RAG", "success", null);
                    String result = ragQaService.answerWithReasoning(userQuestion, factsSufficient, historyFacts);
                    qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                    return result;
                }
                default -> {
                    state = AgentState.FINISHED;
                    logTaskHistory(userQuestion, intentResult.getType().name(), "RAG", "success", null);
                    String result = ragQaService.answerWithReasoning(userQuestion, factsSufficient, historyFacts);
                    qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                    return result;
                }
            }
        } catch (Exception e) {
            qaLogDao.updateResult(logId, null, "FAIL", state.name(), e.getMessage());
            logTaskHistory(userQuestion, null, "UNKNOWN", "fail", e.getMessage());
            throw e;
        }
    }

    private String generateSolutionThenFollowUp(String userQuestion, String reason, String historyFacts, long logId) {
        try {
            state = AgentState.FACT_CHECK;
            logState(userQuestion, null, "事实不足，生成方案摘要后追问");
            lastSolutionSummary = ragQaService.answerWithReasoning(userQuestion, false, historyFacts);
            state = AgentState.SOLUTION_GENERATED;
            logState(userQuestion, null, "已生成方案摘要");
            String prompt = ClarificationPromptBuilder.build(userQuestion, reason, historyFacts);
            state = AgentState.NEED_FOLLOW_UP;
            logState(userQuestion, null, "进入补充信息阶段");
            String result = lastSolutionSummary + "\n\n后续补充信息：\n" + llmClient.chat(prompt);
            qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
            return result;
        } catch (Exception e) {
            qaLogDao.updateResult(logId, null, "FAIL", state.name(), e.getMessage());
            logTaskHistory(userQuestion, null, "RAG", "fail", e.getMessage());
            throw e;
        }
    }

    private String buildFollowUpReminder() {
        return "已进入补充信息阶段，上轮已给出初步意见：\n" +
                (lastSolutionSummary == null ? "" : lastSolutionSummary) +
                "\n本轮不再追加新的追问，请先反馈所需补充信息。";
    }

    private void logState(String userQuestion, LegalIntentResult intentResult, String reason) {
        AgentTaskHistory h = new AgentTaskHistory(this.sessionId, userQuestion,
                intentResult == null ? null : intentResult.getType().name(),
                null, null, reason);
        historyDao.insertHistory(h);
    }

    private void logTaskHistory(String userQuery, String intent, String strategy, String resultStatus, String failReason) {
        AgentTaskHistory h = new AgentTaskHistory();
        h.setSessionId(this.sessionId);
        h.setUserQuery(userQuery);
        h.setIntentType(intent);
        h.setStrategyUsed(strategy);
        h.setResultStatus(resultStatus);
        h.setFailReason(failReason);
        historyDao.insertHistory(h);
    }

    private boolean isRepeatedQuery(String userQuestion, List<AgentTaskHistory> recent) {
        if (userQuestion == null || recent == null || recent.isEmpty()) {
            return false;
        }
        String q = userQuestion.trim();
        for (AgentTaskHistory h : recent) {
            if (h.getUserQuery() != null && h.getUserQuery().trim().equalsIgnoreCase(q)) {
                return true;
            }
        }
        return false;
    }
}
