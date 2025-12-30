package ai.legal.rag.service;

import ai.legal.model.LegalEmbedding;
import ai.legal.rag.prompt.LegalRagPromptBuilder;
import ai.legal.rag.prompt.LegalBasisPromptBuilder;
import ai.legal.rag.prompt.LegalAnalysisPromptBuilder;
import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;
import ai.legal.service.VectorSearchService;

import java.util.List;
import java.util.UUID;

/**
 * 法律 RAG 问答服务：向量检索 + Prompt 构建 + LLM 调用。
 */
public class LegalRagQaService {

    private static final int VECTOR_DIMENSION = 1536;
    private static final int TOP_K = 10;

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
        List<AggregatedLawContext> contexts = retrieveContexts(userQuestion);
        String prompt = LegalRagPromptBuilder.buildPrompt(userQuestion, contexts);
        return llmClient.chat(prompt);
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议。
     */
    public String answerWithReasoning(String userQuestion) {
        return answerWithReasoning(userQuestion, false, "");
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议，可告知事实是否足够。
     */
    public String answerWithReasoning(String userQuestion, boolean factsSufficient) {
        return answerWithReasoning(userQuestion, factsSufficient, "");
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议，并传入已确认事实（conversation history 汇总）。
     */
    public String answerWithReasoning(String userQuestion, boolean factsSufficient, String historyFacts) {
        List<AggregatedLawContext> contexts = retrieveContexts(userQuestion);
        // 检索失败兜底：向量命中与用户请求的法名/条号不一致
        if (contexts.isEmpty() || isInconsistent(userQuestion, contexts)) {
            List<AggregatedLawContext> fallback = retryWithSemanticLocator(userQuestion);
            if (fallback.isEmpty()) {
                return "未找到条文";
            }
            contexts = fallback;
        }
        // 第一阶段：法律依据
        String basisPrompt = LegalBasisPromptBuilder.buildPrompt(userQuestion, contexts);
        String legalBasis = llmClient.chat(basisPrompt);
        // 第二阶段：受控法律分析
        String analysisPrompt = LegalAnalysisPromptBuilder.buildPrompt(userQuestion, legalBasis, contexts, factsSufficient, historyFacts);
        String analysis = llmClient.chat(analysisPrompt);

        StringBuilder result = new StringBuilder();
        result.append("法律依据：\n").append(legalBasis == null ? "" : legalBasis.trim()).append("\n\n");
        result.append("法律分析与建议：\n").append(analysis == null ? "" : analysis.trim());
        return result.toString();
    }

    private List<AggregatedLawContext> retrieveContexts(String userQuestion) {
        double[] queryVector = generateEmbedding(userQuestion);
        List<LegalEmbedding> raw = vectorSearchService.searchTopK(queryVector, TOP_K);
        return ChunkAggregator.aggregate(raw);
    }

    /**
     * 检查检索结果与用户问题中的法名/条号是否一致。
     */
    private boolean isInconsistent(String userQuestion, List<AggregatedLawContext> contexts) {
        if (userQuestion == null || contexts == null || contexts.isEmpty()) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        boolean asksArticle = normalized.matches(".*第[一二三四五六七八九十百0-9]+条.*");
        boolean asksLawName = normalized.contains("法典") || normalized.contains("法") || normalized.contains("条例");
        if (!asksArticle && !asksLawName) {
            return false;
        }
        // 若用户提到条号，要求返回的条文范围包含该条，否则视为不一致
        if (asksArticle) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("第([一二三四五六七八九十百0-9]+)条").matcher(normalized);
            String target = null;
            if (m.find()) {
                target = m.group(1);
            }
            if (target != null) {
                for (AggregatedLawContext ctx : contexts) {
                    if (ctx.getArticleRange() != null && ctx.getArticleRange().contains(target)) {
                        return false; // 找到匹配
                    }
                }
                return true; // 未匹配到用户请求条号
            }
        }
        return false;
    }

    /**
     * 使用 LLM 进行语义定位条号，再尝试结构化查询。
     */
    private List<AggregatedLawContext> retryWithSemanticLocator(String userQuestion) {
        try {
            // 简单提示让 LLM 猜测可能的编/章/条
            String locatorPrompt = "根据用户问题，推测可能的法律编/章/条号，只输出类似“第一编 第一章 第一条”或“第九条”的简短结果：\n" + userQuestion;
            String locator = llmClient.chat(locatorPrompt);
            if (locator == null || locator.isBlank()) {
                return List.of();
            }
            // 将定位结果拼回去做结构化检索
            String combined = userQuestion + " " + locator.trim();
            List<LegalEmbedding> raw = vectorSearchService.searchTopK(generateEmbedding(combined), TOP_K);
            return ChunkAggregator.aggregate(raw);
        } catch (Exception e) {
            return List.of();
        }
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
