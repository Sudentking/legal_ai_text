package ai.legal.console;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.rag.agent.LegalAgentService;
import ai.legal.rag.intent.LegalIntentClassifier;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.rag.service.StructuredLawQueryService;
import ai.legal.service.VectorSearchService;

import java.util.Scanner;

/**
 * 命令行入口：多轮对话，输入→调用→输出。
 */
public class LegalQaCli {

    private static final Long USER_ID = 1L;

    public static void main(String[] args) {
        System.out.println("欢迎使用法律智能问答系统，当前用户ID=" + USER_ID);
        System.out.println("输入法律问题开始咨询，输入 exit 或 quit 退出。");

        LlmClient llmClient = createLlmClient();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LegalRagQaService ragQaService = new LegalRagQaService(vectorSearchService, llmClient);
        StructuredLawQueryService structuredLawQueryService = new StructuredLawQueryService(new LawTextDao());
        LegalAgentService agentService = new LegalAgentService(new LegalIntentClassifier(), ragQaService, structuredLawQueryService, llmClient);

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String question = scanner.nextLine();
                if (question == null) {
                    continue;
                }
                String trimmed = question.trim();
                if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                    System.out.println("已退出。");
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }

                String response = agentService.answer(trimmed);
                System.out.println(response);
            }
        }
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
