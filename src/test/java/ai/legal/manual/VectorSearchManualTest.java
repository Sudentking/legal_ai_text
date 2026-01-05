package ai.legal.manual;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;
import ai.legal.service.VectorSearchService;
import ai.legal.util.EmbeddingUtil;

/**
 * 手工检验 searchTopK 的运行情况。
 */
public class VectorSearchManualTest {

    public static void main(String[] args) throws Exception {
        VectorSearchService service = new VectorSearchService(new LegalEmbeddingDao());
        double[] queryVector = EmbeddingUtil.embed("manual-search");
        System.out.println("Top-5 查询结果：");
        for (LegalEmbedding item : service.searchTopK(queryVector, 5)) {
            System.out.println(format(item));
        }
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
