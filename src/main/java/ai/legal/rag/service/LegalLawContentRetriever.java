package ai.legal.rag.service;

import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * 将现有“先检索法条再生成”的检索逻辑封装为 LangChain4j 的 ContentRetriever，便于后续扩展/替换为框架内置 RAG 组件。
 *
 * <p>当前实现保持项目既有混合检索策略：调用 {@link LegalRagQaService#debugRetrieveContexts(String)}。</p>
 */
public class LegalLawContentRetriever implements ContentRetriever {

    private final LegalRagQaService ragQaService;

    public LegalLawContentRetriever(LegalRagQaService ragQaService) {
        this.ragQaService = ragQaService;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || ragQaService == null) {
            return List.of();
        }
        String text = query.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<AggregatedLawContext> contexts = ragQaService.debugRetrieveContexts(text);
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }
        List<Content> out = new ArrayList<>();
        for (AggregatedLawContext ctx : contexts) {
            if (ctx == null || ctx.getContent() == null || ctx.getContent().isBlank()) {
                continue;
            }
            out.add(Content.from(TextSegment.from(ctx.getContent())));
        }
        return out;
    }
}

