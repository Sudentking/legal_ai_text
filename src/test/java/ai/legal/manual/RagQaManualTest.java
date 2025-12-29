package ai.legal.manual;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.service.VectorSearchService;

/**
 * 手工检验 RAG 闭环（检索 + Prompt + LLM 调用）。
 */
public class RagQaManualTest {

    private static final int MAX_ECHO_LENGTH = 500;

    public static void main(String[] args) throws Exception {
        LlmClient mockClient = prompt -> {
            String preview = prompt.length() > MAX_ECHO_LENGTH
                    ? prompt.substring(0, MAX_ECHO_LENGTH) + "...(截断)"
                    : prompt;
            return "模拟 LLM 回复，回显 Prompt 片段：\n" + preview;
        };

        LegalRagQaService qaService = new LegalRagQaService(
                new VectorSearchService(new LegalEmbeddingDao()),
                mockClient
        );

        String answer = qaService.answer("请说明第1条的适用范围？");
        System.out.println(answer);
    }
}
