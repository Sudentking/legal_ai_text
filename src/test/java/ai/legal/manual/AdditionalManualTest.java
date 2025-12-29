package ai.legal.manual;

import ai.legal.agent.intent.LegalIntent;
import ai.legal.agent.intent.LegalIntentClassifier;
import ai.legal.rag.coordinator.FollowUpRagCoordinator;
import ai.legal.rag.prompt.template.LegalRagPromptBuilder;
import ai.legal.rag.safety.LegalAnswerRiskAnalyzer;
import ai.legal.rag.safety.LegalAnswerRiskLevel;
import ai.legal.rag.safety.LegalSafetyPromptDecorator;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.service.VectorSearchService;
import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖补充测试场景的手工用例。
 */
public class AdditionalManualTest {

    public static void main(String[] args) {
        testRiskAnalyzerAbsoluteKeywords();
        testRagFlowWithMocks();
        testDeepSeekOptional();
    }

    // 验证“必然/一定”等绝对词的风险判定
    private static void testRiskAnalyzerAbsoluteKeywords() {
        System.out.println("== Risk absolute keywords ==");
        LegalAnswerRiskAnalyzer ra = new LegalAnswerRiskAnalyzer();
        List<LegalEmbedding> ctx = sampleEmbeddings();
        LegalAnswerRiskLevel level = ra.assess("必然构成侵权吗", ctx, LegalIntent.LEGAL_SCOPE);
        System.out.println(level); // 预期应为 CONDITIONAL_JUDGMENT
        LegalSafetyPromptDecorator sd = new LegalSafetyPromptDecorator();
        String prompt = sd.decorate("原始Prompt", level);
        System.out.println(prompt);
    }

    // 使用 mock VectorSearch + mock LLM 跑一次完整 RAG 流
    private static void testRagFlowWithMocks() {
        System.out.println("\n== RAG flow with mocks ==");
        LlmClient mockLlm = prompt -> "MOCK_REPLY: " + prompt.substring(0, Math.min(80, prompt.length()));
        VectorSearchService mockSearch = new MockVectorSearchService();
        LegalRagQaService qa = new LegalRagQaService(mockSearch, mockLlm);
        FollowUpRagCoordinator coord = new FollowUpRagCoordinator(qa);
        String answer = coord.answerWithSupplement("第1条如何适用？", "案件事实：租赁合同未到期提前解除");
        System.out.println(answer);

        // 同时查看系统/User Prompt 拼装是否正常
        LegalRagPromptBuilder pb = new LegalRagPromptBuilder();
        System.out.println(pb.buildSystemPrompt());
        System.out.println(pb.buildUserPrompt("第1条如何适用？", "检索到的条文......"));
    }

    // 可选：真实调用 DeepSeek（需环境变量 DEEPSEEK_API_KEY）
    private static void testDeepSeekOptional() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("\n== DeepSeek skipped: missing DEEPSEEK_API_KEY ==");
            return;
        }
        System.out.println("\n== DeepSeek live call ==");
        DeepSeekLlmClient client = new DeepSeekLlmClient(apiKey);
        String resp = client.generate("你是法律助手", "请简述合同的基本要素。");
        System.out.println(resp);
    }

    private static List<LegalEmbedding> sampleEmbeddings() {
        LegalEmbedding e = new LegalEmbedding(1L, "第1条", 0, new double[1536], "示例文本", "来源");
        List<LegalEmbedding> list = new ArrayList<>();
        list.add(e);
        return list;
    }

    private static class MockVectorSearchService extends VectorSearchService {
        MockVectorSearchService() {
            super(new LegalEmbeddingDao());
        }

        @Override
        public List<LegalEmbedding> searchTopK(double[] queryVector, int k) {
            return sampleEmbeddings();
        }
    }
}
