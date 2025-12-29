package ai.legal.manual;

import ai.legal.agent.LegalReasoningAgent;
import ai.legal.agent.AgentDecision;
import ai.legal.agent.intent.LegalIntent;
import ai.legal.agent.intent.LegalIntentClassifier;
import ai.legal.agent.intent.LegalIntentResult;
import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalCitation;
import ai.legal.model.LegalEmbedding;
import ai.legal.rag.coordinator.FollowUpRagCoordinator;
import ai.legal.rag.prompt.template.LegalCitationPromptDecorator;
import ai.legal.rag.prompt.template.LegalRagPromptBuilder;
import ai.legal.rag.safety.LegalAnswerRiskAnalyzer;
import ai.legal.rag.safety.LegalAnswerRiskLevel;
import ai.legal.rag.safety.LegalSafetyPromptDecorator;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.service.VectorSearchService;
import ai.legal.util.LegalCitationExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖近期新增逻辑的手工测试（不依赖数据库）。
 */
public class NewLogicManualTest {

    public static void main(String[] args) {
        testIntentClassifier();
        testRiskAnalyzer();
        testCitationAndPrompt();
        testReasoningAgent();
        testFollowUpCoordinator();
        testAuditPlaceholder(); // 占位调用，确保不抛异常
    }

    private static void testIntentClassifier() {
        System.out.println("== IntentClassifier ==");
        LegalIntentClassifier c = new LegalIntentClassifier();
        System.out.println(c.classify("第1条是什么意思").getIntent());
        System.out.println(c.classify("第1条适用于哪些情况").getIntent());
        System.out.println(c.classify("这样算不算违约").getIntent());
        System.out.println(c.classify("需要承担什么责任").getIntent());
        System.out.println(c.classify("如何起诉").getIntent());
        System.out.println(c.classify("啥?").getIntent());
    }

    private static void testRiskAnalyzer() {
        System.out.println("\n== RiskAnalyzer & SafetyDecorator ==");
        LegalAnswerRiskAnalyzer ra = new LegalAnswerRiskAnalyzer();
        LegalSafetyPromptDecorator sd = new LegalSafetyPromptDecorator();
        List<LegalEmbedding> ctx = sampleEmbeddings();
        System.out.println(ra.assess("必然构成侵权吗", ctx, LegalIntent.LEGAL_SCOPE));
        System.out.println(ra.assess("怎么起诉", ctx, LegalIntent.LEGAL_PROCEDURE));
        System.out.println(ra.assess("算不算违约", ctx, LegalIntent.LEGAL_CONDITION_CHECK));
        System.out.println(ra.assess("短", new ArrayList<>(), LegalIntent.LEGAL_EXPLANATION));
        String decorated = sd.decorate("原始Prompt", LegalAnswerRiskLevel.CONDITIONAL_JUDGMENT);
        System.out.println(decorated.contains("需结合具体事实判断"));
    }

    private static void testCitationAndPrompt() {
        System.out.println("\n== Citation & Prompt ==");
        List<LegalCitation> citations = LegalCitationExtractor.extract(sampleEmbeddings());
        System.out.println("citations size=" + citations.size());
        LegalCitationPromptDecorator dec = new LegalCitationPromptDecorator();
        String prompt = dec.decorate("原Prompt", citations);
        System.out.println(prompt);
        LegalRagPromptBuilder pb = new LegalRagPromptBuilder();
        System.out.println(pb.buildSystemPrompt());
        System.out.println(pb.buildUserPrompt("问题？", "检索条文..."));
    }

    private static void testReasoningAgent() {
        System.out.println("\n== ReasoningAgent ==");
        LegalReasoningAgent agent = new LegalReasoningAgent();
        AgentDecision d1 = agent.decide("算不算违约",
                new LegalIntentResult(LegalIntent.LEGAL_CONDITION_CHECK, 0.9, "测试"));
        System.out.println("allowRag=" + d1.isAllowRag() + ", followUp=" + d1.getFollowUpQuestion());
        AgentDecision d2 = agent.decide("如何起诉",
                new LegalIntentResult(LegalIntent.LEGAL_PROCEDURE, 0.9, "测试"));
        System.out.println("allowRag=" + d2.isAllowRag());
    }

    private static void testFollowUpCoordinator() {
        System.out.println("\n== FollowUpRagCoordinator ==");
        LlmClient mock = prompt -> "MOCK:" + prompt;
        VectorSearchService mockSearch = new MockVectorSearchService();
        LegalRagQaService qa = new LegalRagQaService(mockSearch, mock);
        FollowUpRagCoordinator coord = new FollowUpRagCoordinator(qa);
        String ans = coord.answerWithSupplement("原问题", "用户补充的事实");
        System.out.println(ans.contains("已补充事实：用户补充的事实"));
    }

    private static void testAuditPlaceholder() {
        System.out.println("\n== Audit Placeholder ==");
        // 占位：dao.insert 为空实现，只验证不抛异常
        ai.legal.service.LegalQaAuditService audit =
                new ai.legal.service.LegalQaAuditService(new ai.legal.dao.LegalQaAuditLogDao());
        audit.record("req-1", null, "问题", "LEGAL_SCOPE",
                List.of(1L, 2L), List.of("第1条"), "答案", false);
        System.out.println("audit record ok");
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
