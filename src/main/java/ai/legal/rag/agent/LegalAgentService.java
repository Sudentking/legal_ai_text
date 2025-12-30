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
import ai.legal.rag.service.StructuredLawQueryService;
import ai.legal.service.AgentTaskHistoryService;

import java.util.List;

/**
 * 法律智能 Agent（显式状态机 + 两阶段日志）。
 */
public class LegalAgentService {

    private final LegalIntentClassifier intentClassifier;
    private final LegalRagQaService ragQaService;
    private final StructuredLawQueryService structuredLawQueryService;
    private final FactSufficiencyEvaluator factSufficiencyEvaluator = new FactSufficiencyEvaluator();
    private final AgentTaskHistoryDao historyDao = new AgentTaskHistoryDao();
    private final AgentTaskHistoryService historyService = new AgentTaskHistoryService(historyDao);
    private final QaLogDao qaLogDao = new QaLogDao();
    private final AgentStateMachine stateMachine = new AgentStateMachine();

    private String sessionId = "default-session";
    private Long currentUserId = 1L;
    private int factCollectRounds = 0;

    public LegalAgentService(LegalIntentClassifier intentClassifier,
                             LegalRagQaService ragQaService,
                             StructuredLawQueryService structuredLawQueryService) {
        this.intentClassifier = intentClassifier;
        this.ragQaService = ragQaService;
        this.structuredLawQueryService = structuredLawQueryService;
    }

    public String answer(String userQuestion) {
        return answer(this.currentUserId, this.sessionId, userQuestion, "");
    }

    public String answer(String userQuestion, String historyFacts) {
        return answer(this.currentUserId, this.sessionId, userQuestion, historyFacts);
    }

    public String answer(String sessionId, String userQuestion, String historyFacts) {
        return answer(this.currentUserId, sessionId, userQuestion, historyFacts);
    }

    public String answer(Long userId, String sessionId, String userQuestion, String historyFacts) {
        if (sessionId != null && !sessionId.isBlank()) {
            this.sessionId = sessionId;
        }
        if (userId != null) {
            this.currentUserId = userId;
        }
        String question = userQuestion == null ? "" : userQuestion.trim();
        if (question.isEmpty()) {
            return "";
        }

        // 每轮回答前必须读取最近历史，用于确定性决策（不得拼进 Prompt）
        List<AgentTaskHistory> recent = historyService.findRecent(this.sessionId, 3);
        boolean blockRagByHistory = historyService.recentRagFailedSameQuery(recent, question);

        stateMachine.reset();
        long qaLogId = qaLogDao.insertInit(new QaLog(this.currentUserId, this.sessionId, question, "PENDING", stateMachine.getState().name()));

        long historyId = -1L;
        FsmAgentState finalState = stateMachine.getState();
        try {
            // 1) 法条原文/条文定位：最高优先级，严禁向量检索 & 严禁追问
            boolean hasLawLocator = isLawLocator(question);
            boolean directAsk = wantsDirectAnswer(question);
            if (hasLawLocator) {
                finalState = stateMachine.next(LegalIntentType.LAW_TEXT_QUERY, true, true);

                historyId = historyDao.insertHistory(new AgentTaskHistory(
                        this.sessionId, question, LegalIntentType.LAW_TEXT_QUERY.name(), "SQL", "PENDING", null));

                String structured = structuredLawQueryService.answerIfStructured(question);
                String result = structured != null
                        ? structured
                        : "未识别到可查询的法律名称/章/条编号。请使用例如：“《中华人民共和国民法典》第一编 第一章 第一条 原文”。";

                historyDao.updateResult(historyId, "SUCCESS", null);
                qaLogDao.updateResult(qaLogId, result, "SUCCESS", finalState.name(), null);
                return result;
            }

            // 2) 历史兜底：同一问题上次 RAG 失败，本次禁止再次走向量检索
            if (blockRagByHistory) {
                String structured = structuredLawQueryService.answerIfStructured(question);
                historyId = historyDao.insertHistory(new AgentTaskHistory(
                        this.sessionId, question, null, "SQL", "PENDING", null));
                if (structured != null) {
                    historyDao.updateResult(historyId, "SUCCESS", null);
                    qaLogDao.updateResult(qaLogId, structured, "SUCCESS", FsmAgentState.DIRECT_LAW_QUERY.name(), null);
                    return structured;
                }
                String msg = "检测到同一问题上一次向量检索失败，本次已停止再次使用向量检索。"
                        + "请补充更明确的法律名称与条文编号（例如“《xxx法》第xx条原文”），或换一种问法。";
                historyDao.updateResult(historyId, "FAIL", "history_blocked_rag_and_no_structured_match");
                qaLogDao.updateResult(qaLogId, msg, "SUCCESS", FsmAgentState.FINISHED.name(), null);
                return msg;
            }

            // 3) 意图识别 + 状态机决策
            LegalIntentResult intentResult = intentClassifier.classify(question);
            LegalIntentType intentType = intentResult == null ? null : intentResult.getType();

            // LAW_TEXT_QUERY：仅允许结构化检索，禁止向量检索 & 禁止追问
            if (intentType == LegalIntentType.LAW_TEXT_QUERY) {
                finalState = stateMachine.next(intentType, false, true);
                historyId = historyDao.insertHistory(new AgentTaskHistory(
                        this.sessionId,
                        question,
                        intentType.name(),
                        "SQL",
                        "PENDING",
                        null
                ));
                String structured = structuredLawQueryService.answerIfStructured(question);
                String result = structured != null
                        ? structured
                        : "未识别到可查询的法律名称/章/条编号。请使用例如：“《中华人民共和国民法典》第一编 第一章 第一条 原文”。";
                historyDao.updateResult(historyId, "SUCCESS", null);
                qaLogDao.updateResult(qaLogId, result, "SUCCESS", FsmAgentState.DIRECT_LAW_QUERY.name(), null);
                return result;
            }

            boolean factsSufficient = factSufficiencyEvaluator.isLoanFactsSufficient(
                    historyFacts == null || historyFacts.isBlank() ? question : (question + "\n" + historyFacts));

            FsmAgentState next = stateMachine.next(intentType, false, factsSufficient);
            finalState = next;

            // 4) 事实核查：最多 2 轮追问；用户明确要结论则强制作答
            if (next == FsmAgentState.FACT_CHECK && !factsSufficient && factCollectRounds < 2 && !directAsk) {
                historyId = historyDao.insertHistory(new AgentTaskHistory(
                        this.sessionId,
                        question,
                        intentType == null ? null : intentType.name(),
                        "FACT_CHECK",
                        "PENDING",
                        null
                ));
                factCollectRounds++;
                String prompt = ClarificationPromptBuilder.build(question,
                        intentResult == null ? "需要补充关键事实" : intentResult.getReason(),
                        historyFacts);
                historyDao.updateResult(historyId, "SUCCESS", null);
                qaLogDao.updateResult(qaLogId, prompt, "SUCCESS", FsmAgentState.FACT_CHECK.name(), null);
                return prompt;
            }

            // 5) 进入 RAG + LLM 输出
            historyId = historyDao.insertHistory(new AgentTaskHistory(
                    this.sessionId,
                    question,
                    intentType == null ? null : intentType.name(),
                    "VECTOR",
                    "PENDING",
                    null
            ));
            finalState = stateMachine.next(intentType, false, factsSufficient); // RAG_RETRIEVAL -> LLM_ANSWER
            String result = ragQaService.answerWithReasoning(question, factsSufficient, historyFacts);
            factCollectRounds = 0;

            historyDao.updateResult(historyId, "SUCCESS", null);
            qaLogDao.updateResult(qaLogId, result, "SUCCESS", finalState.name(), null);
            return result;
        } catch (Exception e) {
            String failReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            historyDao.updateResult(historyId, "FAIL", failReason);
            qaLogDao.updateResult(qaLogId, null, "FAIL", finalState.name(), failReason);
            return "系统处理失败：" + failReason;
        }
    }

    private boolean isLawLocator(String userQuestion) {
        if (userQuestion == null) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        return normalized.matches(".*(原文|全文|全部内容|全部条文|第[一二三四五六七八九十百千两0-9]+(编|章|节|条)).*");
    }

    private boolean wantsDirectAnswer(String userQuestion) {
        if (userQuestion == null) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        return normalized.contains("直接给结论") || normalized.contains("直接回答") || normalized.contains("不要追问") || normalized.contains("给出原文");
    }
}
