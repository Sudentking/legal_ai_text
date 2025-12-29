package ai.legal.console;

import ai.legal.agent.intent.LegalIntentClassifier;
import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.rag.agent.LegalAgentService;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.service.VectorSearchService;

import java.util.Scanner;

/**
 * 系统入口：控制台交互版，负责“输入→调用→输出”。
 */
public class LegalQaCli {

    private static final String USER_ID = "1";

    public static void main(String[] args) {
        System.out.println("法律智能问答系统已启动，当前用户ID=" + USER_ID);
        System.out.print("请输入您的法律问题：");

        Scanner scanner = new Scanner(System.in);
        String question = scanner.nextLine();

        LlmClient llmClient = createLlmClient();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LegalRagQaService ragQaService = new LegalRagQaService(vectorSearchService, llmClient);
        LegalAgentService agentService = new LegalAgentService(new LegalIntentClassifier(), ragQaService, llmClient);

        String answer = agentService.answer(question);
        System.out.println("系统回答：");
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
