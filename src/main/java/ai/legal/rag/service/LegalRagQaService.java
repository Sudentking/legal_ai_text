package ai.legal.rag.service;

import ai.legal.model.LegalEmbedding;
import ai.legal.rag.prompt.LegalRagPromptBuilder;
import ai.legal.rag.prompt.LegalBasisPromptBuilder;
import ai.legal.rag.prompt.LegalAnalysisPromptBuilder;
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
        List<LegalEmbedding> contexts = retrieveContexts(userQuestion);
        String prompt = LegalRagPromptBuilder.buildPrompt(userQuestion, contexts);
        return llmClient.chat(prompt);
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议。
     */
    public String answerWithReasoning(String userQuestion) {
        List<LegalEmbedding> contexts = retrieveContexts(userQuestion);
        // 第一阶段：法律依据
        String basisPrompt = LegalBasisPromptBuilder.buildPrompt(userQuestion, contexts);
        String legalBasis = llmClient.chat(basisPrompt);
        // 第二阶段：受控法律分析
        String analysisPrompt = LegalAnalysisPromptBuilder.buildPrompt(userQuestion, legalBasis, contexts);
        String analysis = llmClient.chat(analysisPrompt);

        StringBuilder result = new StringBuilder();
        result.append("法律依据：\n").append(legalBasis == null ? "" : legalBasis.trim()).append("\n\n");
        result.append("法律分析与建议：\n").append(analysis == null ? "" : analysis.trim());
        return result.toString();
    }

    private List<LegalEmbedding> retrieveContexts(String userQuestion) {
        double[] queryVector = generateEmbedding(userQuestion);
        return vectorSearchService.searchTopK(queryVector, TOP_K);
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
