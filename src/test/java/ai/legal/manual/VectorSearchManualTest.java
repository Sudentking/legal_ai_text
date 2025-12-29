package ai.legal.manual;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;
import ai.legal.service.VectorSearchService;

/**
 * 手工检验 searchTopK 的运行情况。
 */
public class VectorSearchManualTest {

    private static final int VECTOR_DIMENSION = 1536;

    public static void main(String[] args) throws Exception {
        VectorSearchService service = new VectorSearchService(new LegalEmbeddingDao());
        double[] queryVector = buildDemoVector("manual-search");
        System.out.println("Top-5 查询结果：");
        for (LegalEmbedding item : service.searchTopK(queryVector, 5)) {
            System.out.println(format(item));
        }
    }

    private static double[] buildDemoVector(String seed) {
        double[] vector = new double[VECTOR_DIMENSION];
        int base = seed == null ? 0 : seed.hashCode();
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (Math.sin(base + i) + 1) * 0.5;
        }
        return vector;
    }

    private static String format(LegalEmbedding item) {
        return "id=" + item.getId()
                + ", law_id=" + item.getLawId()
                + ", article_no=" + item.getArticleNo()
                + ", chunk_index=" + item.getChunkIndex()
                + ", source=" + item.getSource()
                + ", content_preview=" + preview(item.getContent());
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
