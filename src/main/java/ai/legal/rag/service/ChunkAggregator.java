package ai.legal.rag.service;

import ai.legal.model.LegalEmbedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 对向量检索命中的零散条文进行重组，尽量返回连续、同章的条文块。
 */
public class ChunkAggregator {

    public static List<AggregatedLawContext> aggregate(List<LegalEmbedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LegalEmbedding>> grouped = embeddings.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(LegalEmbedding::getLawId, LinkedHashMap::new, Collectors.toList()));

        List<AggregatedLawContext> result = new ArrayList<>();
        for (Map.Entry<Long, List<LegalEmbedding>> entry : grouped.entrySet()) {
            List<LegalEmbedding> sorted = entry.getValue().stream()
                    .sorted(Comparator
                            .comparingInt((LegalEmbedding e) -> parseArticleNo(e.getArticleNo()))
                            .thenComparingInt(LegalEmbedding::getChunkIndex))
                    .toList();
            AggregatedLawContext current = null;
            for (LegalEmbedding e : sorted) {
                int art = parseArticleNo(e.getArticleNo());
                if (current == null || !current.canMerge(art)) {
                    current = AggregatedLawContext.start(entry.getKey(), art, e.getArticleNo(), e.getContent());
                    result.add(current);
                } else {
                    current.merge(art, e.getArticleNo(), e.getContent());
                }
            }
        }
        return result;
    }

    private static int parseArticleNo(String articleNo) {
        if (articleNo == null) {
            return -1;
        }
        String digits = articleNo.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static class AggregatedLawContext {
        private final long lawId;
        private int startArticle;
        private int endArticle;
        private final List<String> articleLabels = new ArrayList<>();
        private final StringBuilder content = new StringBuilder();

        private AggregatedLawContext(long lawId, int start, String label, String text) {
            this.lawId = lawId;
            this.startArticle = start;
            this.endArticle = start;
            if (label != null && !label.isEmpty()) {
                articleLabels.add(label);
            }
            append(text);
        }

        static AggregatedLawContext start(long lawId, int article, String label, String text) {
            return new AggregatedLawContext(lawId, article, label, text);
        }

        boolean canMerge(int nextArticle) {
            if (nextArticle == -1 || startArticle == -1) {
                return false;
            }
            return nextArticle - endArticle <= 1;
        }

        void merge(int article, String label, String text) {
            if (article != -1) {
                endArticle = Math.max(endArticle, article);
            }
            if (label != null && !label.isEmpty()) {
                articleLabels.add(label);
            }
            append(text);
        }

        private void append(String text) {
            if (text != null && !text.isEmpty()) {
                if (content.length() > 0 && content.charAt(content.length() - 1) != '\n') {
                    content.append("\n");
                }
                content.append(text.trim()).append("\n");
            }
        }

        public long getLawId() {
            return lawId;
        }

        public String getArticleRange() {
            if (startArticle == -1 || endArticle == -1) {
                if (!articleLabels.isEmpty()) {
                    return String.join("、", articleLabels);
                }
                return "未知条文";
            }
            if (startArticle == endArticle) {
                return "第" + startArticle + "条";
            }
            return "第" + startArticle + "条-第" + endArticle + "条";
        }

        public String getCoverageNote() {
            if (startArticle == -1 || endArticle == -1) {
                return "部分条文片段（条号缺失）";
            }
            if (startArticle == endArticle) {
                return "单条条文块";
            }
            return "连续条文块";
        }

        public String getContent() {
            return content.toString().trim();
        }
    }
}
