package ai.legal.manual;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.service.VectorSearchService;

import java.util.List;

/**
 * 轻量离线评测/排障工具：观察“某个问题”在当前检索策略下召回了哪些条文。
 *
 * <p>用法：
 * <pre>
 *   java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.manual.RetrievalEvalMain "欠钱不还怎么办"
 *   java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.manual.RetrievalEvalMain "民法典第一编第一章第一条原文" "第一条"
 * </pre>
 */
public class RetrievalEvalMain {

    public static void main(String[] args) {
        if (args == null || args.length == 0 || args[0].isBlank()) {
            System.out.println("用法：RetrievalEvalMain <query> [expectContains]");
            return;
        }
        String query = args[0].trim();
        String expect = args.length >= 2 ? args[1].trim() : null;

        LegalRagQaService qa = new LegalRagQaService(
                new VectorSearchService(new LegalEmbeddingDao()),
                prompt -> "MOCK_LLM_REPLY"
        );

        List<AggregatedLawContext> contexts = qa.debugRetrieveContexts(query);
        System.out.println("query=" + query);
        System.out.println("contexts.size=" + contexts.size());
        for (int i = 0; i < contexts.size(); i++) {
            AggregatedLawContext c = contexts.get(i);
            System.out.println("[" + (i + 1) + "] law_id=" + c.getLawId()
                    + " range=" + c.getArticleRange()
                    + " note=" + c.getCoverageNote());
            System.out.println(preview(c.getContent(), 260));
            System.out.println();
        }

        if (expect != null && !expect.isBlank()) {
            boolean hit = contexts.stream().anyMatch(c ->
                    (c.getArticleRange() != null && c.getArticleRange().contains(expect)) ||
                            (c.getContent() != null && c.getContent().contains(expect)));
            System.out.println("expectContains=" + expect + " hit=" + hit);
        }
    }

    private static String preview(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...(截断)";
    }
}
