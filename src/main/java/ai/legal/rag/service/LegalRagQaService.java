package ai.legal.rag.service;

import ai.legal.model.LegalEmbedding;
import ai.legal.rag.prompt.LegalRagPromptBuilder;
import ai.legal.service.VectorSearchService;

import java.util.List;
import java.util.UUID;

/**
 * 法律 RAG 问答服务：向量检索 + Prompt 构建 + LLM 调用。
 */
public class LegalRagQaService {

    private static final int VECTOR_DIMENSION = 1536;
    private static final int TOP_K = 5;

    private final VectorSearchService vectorSearchService;
    private final LlmClient llmClient;

    public LegalRagQaService(VectorSearchService vectorSearchService, LlmClient llmClient) {
        this.vectorSearchService = vectorSearchService;
        this.llmClient = llmClient;
    }

    /**
     * 基于 RAG 流程返回问答结果。
     *
     * @param userQuestion 用户问题
     * @return 模型回答
     */
    public String answer(String userQuestion) {
        double[] queryVector = generateEmbedding(userQuestion);
        List<LegalEmbedding> contexts = vectorSearchService.searchTopK(queryVector, TOP_K);
        String prompt = LegalRagPromptBuilder.buildPrompt(userQuestion, contexts);
        return llmClient.chat(prompt);
    }

    /**
     * 将问题转换为 1536 维向量，采用与导入阶段一致的确定性生成方式。
     */
    private double[] generateEmbedding(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        String seed = text == null ? UUID.randomUUID().toString() : text;
        int base = seed.hashCode();
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            vector[i] = (Math.sin(base + i) + 1) * 0.5;
        }
        return vector;
    }
}
