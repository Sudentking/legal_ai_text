package ai.legal.rag.agent;

import ai.legal.rag.intent.LegalIntentClassifier;
import ai.legal.rag.intent.LegalIntentResult;
import ai.legal.rag.intent.LegalIntentType;
import ai.legal.rag.prompt.ClarificationPromptBuilder;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;

/**
 * 法律智能 Agent：意图识别 → 决策 → RAG → LLM。
 */
public class LegalAgentService {

    private final LegalIntentClassifier intentClassifier;
    private final LegalRagQaService ragQaService;
    private final LlmClient llmClient;

    public LegalAgentService(LegalIntentClassifier intentClassifier,
                             LegalRagQaService ragQaService,
                             LlmClient llmClient) {
        this.intentClassifier = intentClassifier;
        this.ragQaService = ragQaService;
        this.llmClient = llmClient;
    }

    /**
     * 完整问答闭环。
     *
     * @param userQuestion 用户问题
     * @return 模型回答或澄清问题
     */
    public String answer(String userQuestion) {
        LegalIntentResult intentResult = intentClassifier.classify(userQuestion);
        // 1) 法条原文查询：视为信息充分，直接进入 RAG
        if (intentResult.getType() == LegalIntentType.LAW_TEXT_QUERY) {
            // 设计说明：用户明确指定法律名称/章节/条款，属于可直接回答型查询，不应阻断。
            return ragQaService.answer(userQuestion);
        }
        // 2) 责任/条件判断：需要事实支撑，优先触发追问
        if (intentResult.getType() == LegalIntentType.LEGAL_LIABILITY
                || intentResult.getType() == LegalIntentType.LEGAL_CONDITION_CHECK) {
            String prompt = ClarificationPromptBuilder.build(userQuestion, "责任或条件判断需要明确主体、行为、时间、后果等事实");
            return llmClient.chat(prompt);
        }
        // 3) 置信度不足或显式不足：兜底追问
        if (intentResult.getType() == LegalIntentType.NEEDS_FACTS || intentResult.getConfidence() < 0.6) {
            String prompt = ClarificationPromptBuilder.build(userQuestion, intentResult.getReason());
            return llmClient.chat(prompt);
        }
        // 4) 解释/适用范围/流程等：默认认为信息足够，可直接进入 RAG
        return ragQaService.answer(userQuestion);
    }
}
