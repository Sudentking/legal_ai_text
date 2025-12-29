package ai.legal.rag.service;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 针对“明确法律名称 + 章/条/全文”类问题的快速路由：
 * 命中后直接从 MySQL 返回原文，跳过向量检索，避免检索误差导致拒答。
 */
public class StructuredLawQueryService {

    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,30}(法典|法|条例|规定|办法|规章|解释))");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第([一二三四五六七八九十百千0-9]+)章");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("第([一二三四五六七八九十百千0-9]+)条");
    private static final Pattern FULL_PATTERN = Pattern.compile("(全文|全部内容|全部条文|整部|所有条文|全章)");

    private final LawTextDao lawTextDao;

    public StructuredLawQueryService(LawTextDao lawTextDao) {
        this.lawTextDao = lawTextDao;
    }

    /**
     * 命中结构化法条查询时返回原文，未命中返回 null。
     */
    public String answerIfStructured(String question) {
        StructuredQuery query = parse(question);
        if (query == null) {
            return null;
        }

        // 优先条 → 章 → 全文
        if (query.article != null) {
            List<LawText> byArticle = lawTextDao.findByLawNameAndArticle(query.lawName, query.article);
            if (!byArticle.isEmpty()) {
                return formatLawTexts(byArticle, "未在数据库找到对应条文");
            }
        }
        if (query.chapter != null) {
            List<LawText> byChapter = lawTextDao.findByLawNameAndChapter(query.lawName, query.chapter);
            if (!byChapter.isEmpty()) {
                return formatLawTexts(byChapter, "未在数据库找到对应章节");
            }
        }
        if (query.askFullLaw) {
            List<LawText> all = lawTextDao.findByLawName(query.lawName);
            if (!all.isEmpty()) {
                return formatLawTexts(all, "未在数据库找到对应法律全文");
            }
        }
        // 命中了结构化模式但没有数据，不走向量检索，直接提示。
        return "未在数据库找到匹配的条文，请确认法律名称或编号是否正确。";
    }

    private StructuredQuery parse(String question) {
        if (question == null) {
            return null;
        }
        String normalized = question.replaceAll("\\s+", "");
        Matcher lawMatcher = LAW_NAME_PATTERN.matcher(normalized);
        if (!lawMatcher.find()) {
            return null;
        }
        String lawName = lawMatcher.group(0);
        String chapter = findFirstGroup(normalized, CHAPTER_PATTERN);
        String article = findFirstGroup(normalized, ARTICLE_PATTERN);
        boolean askFull = FULL_PATTERN.matcher(normalized).find();

        // 需要出现条/章/全文任一才视为结构化查询
        if (chapter == null && article == null && !askFull) {
            return null;
        }

        StructuredQuery query = new StructuredQuery();
        query.lawName = lawName;
        query.chapter = chapter == null ? null : "第" + chapter + "章";
        query.article = article == null ? null : "第" + article + "条";
        query.askFullLaw = askFull;
        return query;
    }

    private String findFirstGroup(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String formatLawTexts(List<LawText> lawTexts, String emptyMessage) {
        if (lawTexts == null || lawTexts.isEmpty()) {
            return emptyMessage;
        }
        StringBuilder sb = new StringBuilder();
        for (LawText lt : new ArrayList<>(lawTexts)) {
            sb.append(safe(lt.getLawTitle()));
            if (lt.getArticleNumber() != null && !lt.getArticleNumber().isEmpty()) {
                sb.append(" ").append(lt.getArticleNumber());
            }
            sb.append("\n").append(safe(lt.getFullText())).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private static class StructuredQuery {
        String lawName;
        String chapter;
        String article;
        boolean askFullLaw;
    }
}
