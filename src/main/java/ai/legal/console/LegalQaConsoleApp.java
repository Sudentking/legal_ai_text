package ai.legal.console;

import ai.legal.agent.AgentDecision;
import ai.legal.agent.LegalReasoningAgent;
import ai.legal.agent.intent.LegalIntentClassifier;
import ai.legal.agent.intent.LegalIntentResult;
import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.rag.coordinator.FollowUpRagCoordinator;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.service.VectorSearchService;

/**
 * 面向终端的业务入口：意图识别 → 事实检查 → RAG 闭环。
 * 用法示例：
 * java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.LegalQaConsoleApp "问题" ["补充事实"]
 */
public class LegalQaConsoleApp {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java ... ai.legal.console.LegalQaConsoleApp \"问题\" [\"补充事实\"]");
            return;
        }
        String question = args[0];
        String supplement = args.length > 1 ? args[1] : null;

        LlmClient llmClient = createLlmClient();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LegalRagQaService ragQaService = new LegalRagQaService(vectorSearchService, llmClient);
        FollowUpRagCoordinator coordinator = new FollowUpRagCoordinator(ragQaService);

        LegalIntentClassifier classifier = new LegalIntentClassifier();
        LegalIntentResult intentResult = classifier.classify(question);
        LegalReasoningAgent reasoningAgent = new LegalReasoningAgent();
        AgentDecision decision = reasoningAgent.decide(question, intentResult);

        if (!decision.isAllowRag()) {
            System.out.println("提示：当前信息不足，需补充事实后再回答。");
            System.out.println(decision.getFollowUpQuestion());
            return;
        }

        String answer;
        if (supplement != null && !supplement.trim().isEmpty()) {
            answer = coordinator.answerWithSupplement(question, supplement);
        } else {
            answer = ragQaService.answer(question);
        }
        System.out.println("回答：");
        System.out.println(answer);
    }

    private static LlmClient createLlmClient() {
        try {
            return new DeepSeekLlmClient();
        } catch (Exception e) {
            System.out.println("警告：DeepSeek 未配置或不可用，使用 Mock LLM。原因: " + e.getMessage());
            return prompt -> "MOCK_LLM_REPLY: " + prompt;
        }
    }
}
