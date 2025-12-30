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

/**
 * 法律智能 Agent：意图识别 → 决策 → RAG → LLM。
 */
public class LegalAgentService {

    private final LegalIntentClassifier intentClassifier;
    private final LegalRagQaService ragQaService;
    private final StructuredLawQueryService structuredLawQueryService;
    private final FactSufficiencyEvaluator factSufficiencyEvaluator = new FactSufficiencyEvaluator();
    private final LlmClient llmClient;
    private final AgentTaskHistoryDao historyDao = new AgentTaskHistoryDao();
    private final QaLogDao qaLogDao = new QaLogDao();
    private String sessionId = "default-session";
    private Long currentUserId = 1L;
    // 简单的有状态控制，防止无限追问
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

    /**
     * 完整问答闭环。
     *
     * @param userQuestion 用户问题
     * @return 模型回答或澄清问题
     */
    public String answer(String userQuestion) {
        return answer(userQuestion, "");
    }

    /**
     * 支持传入对话历史中已确认的事实摘要，避免重复追问。
     */
    public String answer(String userQuestion, String historyFacts) {
        return answer(sessionId, userQuestion, historyFacts);
    }

    /**
     * 支持传入 sessionId，用于读取历史与写入历史。
     */
    public String answer(String sessionId, String userQuestion, String historyFacts) {
        return answer(this.currentUserId, sessionId, userQuestion, historyFacts);
    }

    /**
        * 完整入口：带 userId + sessionId + 已确认事实。
        */
    public String answer(Long userId, String sessionId, String userQuestion, String historyFacts) {
        if (sessionId != null && !sessionId.isBlank()) {
            this.sessionId = sessionId;
        }
        if (userId != null) {
            this.currentUserId = userId;
        }
        // 1) 先记录 INIT 日志，防止异常时无记录
        QaLog qaLog = new QaLog(this.currentUserId, this.sessionId, userQuestion, "INIT", state.name());
        long logId = qaLogDao.insertInit(qaLog);
        try {
            // 读取最近历史，用于决策
            var recent = historyDao.findRecentBySession(this.sessionId, 5);
            if (isRepeatedQuery(userQuestion, recent)) {
                state = AgentState.SOLUTION_GENERATED;
                logState(userQuestion, null, "重复问题，直接输出方案");
                String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }
            if (lastTwoAreFollowUp(recent)) {
                state = AgentState.FINISHED;
                logState(userQuestion, null, "连续追问已达上限，输出总结");
                String result = lastSolutionSummary.isEmpty() ? "已进入补充信息阶段，建议结合已确认事实输出总结。" : lastSolutionSummary;
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }

            // 若上一轮已经在补充信息阶段，则本轮不再重复进入事实核查，直接提醒用户补充。
            if (state == AgentState.NEED_FOLLOW_UP) {
                String result = buildFollowUpReminder();
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }
            // 每轮开始重置为 INIT，再按规则迁移，确保一次输入只触发明确的迁移链路。
            state = AgentState.INIT;
            logState(userQuestion, null, "初始化");
            // 0) 优先：命中“法律名称 + 章/条/全文”结构化查询，直接返回数据库原文
            String structured = structuredLawQueryService.answerIfStructured(userQuestion);
            if (structured != null) {
                state = AgentState.FINISHED;
                logState(userQuestion, null, "结构化命中，直接返回");
                qaLogDao.updateResult(logId, structured, "SUCCESS", state.name(), null);
                return structured;
            }
            LegalIntentResult intentResult = intentClassifier.classify(userQuestion);
            state = AgentState.INTENT_RECOGNIZED;
            logState(userQuestion, intentResult, "意图识别");
            boolean factsSufficient = factSufficiencyEvaluator.isLoanFactsSufficient(userQuestion);
            // 1) 法条原文查询：视为信息充分，直接进入 RAG
            if (intentResult.getType() == LegalIntentType.LAW_TEXT_QUERY) {
                state = AgentState.FINISHED;
                logState(userQuestion, intentResult, "法条原文直接回答");
                String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                return result;
            }
            // 2) 责任/条件判断：需要事实支撑，优先触发追问
            if (intentResult.getType() == LegalIntentType.LEGAL_LIABILITY
                    || intentResult.getType() == LegalIntentType.LEGAL_CONDITION_CHECK) {
                if (factsSufficient) {
                    state = AgentState.FINISHED;
                    logState(userQuestion, intentResult, "事实充分，直接输出");
                    String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                    qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                    return result;
                }
                return generateSolutionThenFollowUp(userQuestion, intentResult.getReason(), historyFacts, logId);
            }
            // 3) 置信度不足或显式不足：兜底追问
            if (intentResult.getType() == LegalIntentType.NEEDS_FACTS || intentResult.getConfidence() < 0.6) {
                if (factsSufficient) {
                    state = AgentState.FINISHED;
                    logState(userQuestion, intentResult, "事实充分，直接输出");
                    String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                    qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
                    return result;
                }
                return generateSolutionThenFollowUp(userQuestion, intentResult.getReason(), historyFacts, logId);
            }
            // 4) 解释/适用范围/流程等：默认认为信息足够，可直接进入 RAG
            state = AgentState.FINISHED;
            logState(userQuestion, intentResult, "解释/适用/流程，直接输出");
            String result = ragQaService.answerWithReasoning(userQuestion, factsSufficient, historyFacts);
            qaLogDao.updateResult(logId, result, "SUCCESS", state.name(), null);
            return result;
        } catch (Exception e) {
            qaLogDao.updateResult(logId, null, "FAIL", state.name(), e.getMessage());
            throw e;
        }
    }

    private String generateSolutionThenFollowUp(String userQuestion, String reason, String historyFacts, long logId) {
        // 先给出初步方案摘要，再进入 NEED_FOLLOW_UP，避免无限追问
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
            throw e;
        }
    }

    private String buildFollowUpReminder() {
        return "已进入补充信息阶段，上轮已给出初步意见：\n" +
                (lastSolutionSummary == null ? "" : lastSolutionSummary) +
                "\n本轮不再追加新的追问，请先反馈所需补充信息。";
    }

    private void logState(String userQuestion, LegalIntentResult intentResult, String reason) {
        AgentTaskHistory h = new AgentTaskHistory(this.sessionId, userQuestion, state.name(),
                intentResult == null ? null : intentResult.getType().name(),
                reason);
        historyDao.insertHistory(h);
    }

    private boolean lastTwoAreFollowUp(java.util.List<AgentTaskHistory> recent) {
        if (recent == null || recent.size() < 2) {
            return false;
        }
        return "NEED_FOLLOW_UP".equals(recent.get(0).getAgentState())
                && "NEED_FOLLOW_UP".equals(recent.get(1).getAgentState());
    }

    private boolean isRepeatedQuery(String userQuestion, java.util.List<AgentTaskHistory> recent) {
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
