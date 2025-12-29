package ai.legal.util;

import ai.legal.model.LegalCitation;
import ai.legal.model.LegalEmbedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 从向量检索结果中提取引用信息。
 */
public class LegalCitationExtractor {

    private LegalCitationExtractor() {
    }

    /**
     * 将 LegalEmbedding 列表转换为引用列表。
     *
     * @param embeddings 检索结果
     * @return 引用列表
     */
    public static List<LegalCitation> extract(List<LegalEmbedding> embeddings) {
        List<LegalCitation> citations = new ArrayList<>();
        if (embeddings == null) {
            return citations;
        }
        for (LegalEmbedding embedding : embeddings) {
            if (embedding == null) {
                continue;
            }
            LegalCitation citation = new LegalCitation(
                    embedding.getLawId(),
                    embedding.getArticleNo(),
                    null,
                    embedding.getChunkIndex()
            );
            citations.add(citation);
        }
        return citations;
    }
}
