package ai.legal.manual;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;
import ai.legal.rag.prompt.LegalRagPromptBuilder;
import ai.legal.service.VectorSearchService;

import java.util.List;

/**
 * 手工检验 Prompt 拼装输出。
 */
public class PromptBuilderManualTest {

    private static final int VECTOR_DIMENSION = 1536;

    public static void main(String[] args) throws Exception {
        VectorSearchService service = new VectorSearchService(new LegalEmbeddingDao());
        double[] queryVector = buildDemoVector("prompt-test");
        List<LegalEmbedding> contexts = service.searchTopK(queryVector, 5);
        String prompt = LegalRagPromptBuilder.buildPrompt("请说明第1条的适用范围？", contexts);
        System.out.println(prompt);
    }

    private static double[] buildDemoVector(String seed) {
        double[] vector = new double[VECTOR_DIMENSION];
        int base = seed == null ? 0 : seed.hashCode();
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (Math.sin(base + i) + 1) * 0.5;
        }
        return vector;
    }
}
