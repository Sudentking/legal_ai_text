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
        if (intentResult.getType() == LegalIntentType.NEEDS_FACTS) {
            String prompt = ClarificationPromptBuilder.build(userQuestion, intentResult.getReason());
            return llmClient.chat(prompt);
        }
        // 走标准 RAG 闭环
        return ragQaService.answer(userQuestion);
    }
}
