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
 * 法律智能 Agent（显式状态机 + 两阶段日志）。
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

    private String sessionId = "default-session";
    private Long currentUserId = 1L;
    private DialogueState dialogueState = DialogueState.INIT;
    private int factCollectRounds = 0;
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
        if (dialogueState == DialogueState.TERMINATED) {
            return lastSolutionSummary == null ? "本轮已结束，如需继续请提出新问题。" : lastSolutionSummary;
        }

        long logId = qaLogDao.insertInit(new QaLog(this.currentUserId, this.sessionId, userQuestion, "PENDING", dialogueState.name()));
        try {
            List<AgentTaskHistory> recent = historyService.findRecent(this.sessionId, 5);

            // 重复问题直接出答案
            if (isRepeatedQuery(userQuestion, recent)) {
                dialogueState = DialogueState.ANSWER_READY;
                String result = ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                dialogueState = DialogueState.ANSWERED;
                dialogueState = DialogueState.TERMINATED;
                lastSolutionSummary = result;
                qaLogDao.updateResult(logId, result, "SUCCESS", dialogueState.name(), null);
                return result;
            }

            dialogueState = DialogueState.INIT;

            // 法条原文直通或用户要求直接结论
            boolean lawLocator = isLawLocator(userQuestion);
            boolean directAsk = wantsDirectAnswer(userQuestion);
            if (lawLocator || directAsk) {
                dialogueState = DialogueState.ANSWER_READY;
                String structured = structuredLawQueryService.answerIfStructured(userQuestion);
                String result = structured != null ? structured : ragQaService.answerWithReasoning(userQuestion, true, historyFacts);
                dialogueState = DialogueState.ANSWERED;
                dialogueState = DialogueState.TERMINATED;
                lastSolutionSummary = result;
                qaLogDao.updateResult(logId, result, "SUCCESS", dialogueState.name(), null);
                return result;
            }

            // 结构化兜底
            String structured = structuredLawQueryService.answerIfStructured(userQuestion);
            if (structured != null) {
                dialogueState = DialogueState.ANSWERED;
                dialogueState = DialogueState.TERMINATED;
                lastSolutionSummary = structured;
                qaLogDao.updateResult(logId, structured, "SUCCESS", dialogueState.name(), null);
                return structured;
            }

            // 意图与事实判断
            LegalIntentResult intentResult = intentClassifier.classify(userQuestion);
            boolean factsSufficient = factSufficiencyEvaluator.isLoanFactsSufficient(userQuestion);
            boolean needFacts = intentResult.getType() == LegalIntentType.LEGAL_LIABILITY
                    || intentResult.getType() == LegalIntentType.LEGAL_CONDITION_CHECK
                    || intentResult.getType() == LegalIntentType.NEEDS_FACTS
                    || intentResult.getConfidence() < 0.6;

            if (needFacts && !factsSufficient && factCollectRounds < 2) {
                dialogueState = DialogueState.FACT_COLLECTING;
                factCollectRounds++;
                String prompt = ClarificationPromptBuilder.build(userQuestion, intentResult.getReason(), historyFacts);
                qaLogDao.updateResult(logId, prompt, "SUCCESS", dialogueState.name(), null);
                return prompt;
            }

            // 准备作答，不再追问
            dialogueState = DialogueState.ANSWER_READY;
            String result = ragQaService.answerWithReasoning(userQuestion, factsSufficient, historyFacts);
            dialogueState = DialogueState.ANSWERED;
            dialogueState = DialogueState.TERMINATED;
            factCollectRounds = 0;
            lastSolutionSummary = result;
            qaLogDao.updateResult(logId, result, "SUCCESS", dialogueState.name(), null);
            return result;
        } catch (Exception e) {
            qaLogDao.updateResult(logId, null, "FAIL", dialogueState.name(), e.getMessage());
            throw e;
        }
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

    private boolean isLawLocator(String userQuestion) {
        if (userQuestion == null) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        return normalized.matches(".*(原文|全文|内容|第[一二三四五六七八九十百0-9]+(编|章|节|条)).*");
    }

    private boolean wantsDirectAnswer(String userQuestion) {
        if (userQuestion == null) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        return normalized.contains("直接给结论") || normalized.contains("直接回答") || normalized.contains("不要追问") || normalized.contains("给出原文");
    }
}
