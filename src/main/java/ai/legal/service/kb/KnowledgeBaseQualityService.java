package ai.legal.service.kb;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;
import ai.legal.util.LawNumberingUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 知识库质量检查：用于发现导入/解析导致的编号错乱、重复、缺失等问题。
 *
 * <p>说明：本服务仅做“可疑信号”检查，不做强行修复（修复应通过重导入/重建完成）。
 */
public class KnowledgeBaseQualityService {

    private static final int MAX_MISSING_REPORT = 200;
    private static final int MAX_DUPLICATE_REPORT = 200;
    private static final int MAX_ORDER_ISSUES_REPORT = 200;

    private final LawTextDao lawTextDao;

    public KnowledgeBaseQualityService(LawTextDao lawTextDao) {
        this.lawTextDao = lawTextDao;
    }

    /**
     * 对指定 law_code 做质量检查，返回可 JSON 序列化的 Map。
     */
    public Map<String, Object> checkLawCode(String lawCode) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("lawCode", lawCode);
        if (lawCode == null || lawCode.isBlank()) {
            report.put("success", false);
            report.put("message", "lawCode_required");
            return report;
        }

        String code = lawCode.trim();
        List<LawText> articles = lawTextDao.findArticlesByLawCode(code);
        report.put("totalArticles", articles.size());
        report.putAll(lawTextDao.getChunkVectorStatsByLawCode(code));

        // 提取可解析的条号
        List<ArticleMeta> metas = new ArrayList<>();
        List<Long> unknownArticleNoIds = new ArrayList<>();
        for (LawText t : articles) {
            Integer no = LawNumberingUtil.parseArticleNo(t.getArticleNumber());
            Integer chapterNo = LawNumberingUtil.parseChapterNoFromTitle(t.getLawTitle());
            if (no == null) {
                unknownArticleNoIds.add(t.getId());
                continue;
            }
            metas.add(new ArticleMeta(t.getId(), no, chapterNo, t.getArticleNumber(), t.getLawTitle()));
        }
        report.put("parsedArticleCount", metas.size());
        report.put("unknownArticleNoCount", unknownArticleNoIds.size());
        if (!unknownArticleNoIds.isEmpty()) {
            report.put("unknownArticleNoIds", unknownArticleNoIds.stream().limit(100).toList());
        }

        // 重复检测：同一条号出现多个 id
        Map<Integer, List<Long>> dup = new LinkedHashMap<>();
        for (ArticleMeta m : metas) {
            dup.computeIfAbsent(m.articleNo, k -> new ArrayList<>()).add(m.id);
        }
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (Map.Entry<Integer, List<Long>> e : dup.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicates.add(Map.of("articleNo", e.getKey(), "ids", e.getValue()));
                if (duplicates.size() >= MAX_DUPLICATE_REPORT) {
                    break;
                }
            }
        }
        report.put("duplicateArticleNoCount", duplicates.size());
        if (!duplicates.isEmpty()) {
            report.put("duplicates", duplicates);
        }

        // 缺失检测：在最小~最大范围内，统计缺失编号（仅在范围不过大时有意义）
        metas.sort(Comparator.comparingInt(a -> a.articleNo));
        LinkedHashSet<Integer> existing = new LinkedHashSet<>();
        for (ArticleMeta m : metas) {
            existing.add(m.articleNo);
        }
        List<Integer> missing = new ArrayList<>();
        if (!existing.isEmpty()) {
            int min = existing.iterator().next();
            int max = metas.get(metas.size() - 1).articleNo;
            // 仅当范围合理时输出缺失编号，避免整部大法输出过长
            if (max - min <= 5000) {
                for (int i = min; i <= max; i++) {
                    if (!existing.contains(i)) {
                        missing.add(i);
                        if (missing.size() >= MAX_MISSING_REPORT) {
                            break;
                        }
                    }
                }
            }
            report.put("articleNoMin", min);
            report.put("articleNoMax", max);
        }
        report.put("missingArticleNoCount", missing.size());
        if (!missing.isEmpty()) {
            report.put("missingArticleNos", missing);
        }

        // 顺序异常：按 id 排序时条号不应频繁回退（可能是解析/导入错乱）
        metas.sort(Comparator.comparingLong(a -> a.id));
        List<Map<String, Object>> orderIssues = new ArrayList<>();
        Integer prev = null;
        for (ArticleMeta m : metas) {
            if (prev != null && m.articleNo < prev) {
                orderIssues.add(Map.of("id", m.id, "articleNo", m.articleNo, "prevArticleNo", prev));
                if (orderIssues.size() >= MAX_ORDER_ISSUES_REPORT) {
                    break;
                }
            }
            prev = m.articleNo;
        }
        report.put("orderIssueCount", orderIssues.size());
        if (!orderIssues.isEmpty()) {
            report.put("orderIssues", orderIssues);
        }

        // 章号与条号的可疑关系：同一条号出现多个章号（可能章节错分或重复导入）
        Map<Integer, Integer> articleToChapter = new LinkedHashMap<>();
        List<Map<String, Object>> chapterConflicts = new ArrayList<>();
        for (ArticleMeta m : metas) {
            if (m.chapterNo == null) {
                continue;
            }
            Integer old = articleToChapter.putIfAbsent(m.articleNo, m.chapterNo);
            if (old != null && !old.equals(m.chapterNo)) {
                chapterConflicts.add(Map.of("articleNo", m.articleNo, "chapterNo1", old, "chapterNo2", m.chapterNo, "id", m.id));
                if (chapterConflicts.size() >= 100) {
                    break;
                }
            }
        }
        report.put("chapterConflictCount", chapterConflicts.size());
        if (!chapterConflicts.isEmpty()) {
            report.put("chapterConflicts", chapterConflicts);
        }

        report.put("success", true);
        report.put("message", "ok");
        return report;
    }

    private static final class ArticleMeta {
        private final long id;
        private final int articleNo;
        private final Integer chapterNo;
        private final String articleLabel;
        private final String lawTitle;

        private ArticleMeta(long id, int articleNo, Integer chapterNo, String articleLabel, String lawTitle) {
            this.id = id;
            this.articleNo = articleNo;
            this.chapterNo = chapterNo;
            this.articleLabel = articleLabel;
            this.lawTitle = lawTitle;
        }
    }
}

