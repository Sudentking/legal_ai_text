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

    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{2,30}?(法典|法|条例|规定|办法|规章|解释))");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第([一二三四五六七八九十百千0-9]+)章");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("第([一二三四五六七八九十百千0-9]+)条");
    private static final Pattern PART_PATTERN = Pattern.compile("第([一二三四五六七八九十百千0-9]+)(编|节)");
    private static final Pattern GENERAL_PATTERN = Pattern.compile("(总则|附则)");
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
        if (query.keyword != null && !query.keyword.isBlank()) {
            List<LawText> byKeyword = lawTextDao.searchByLawAndKeyword(query.lawName, query.keyword, 10);
            if (!byKeyword.isEmpty()) {
                return formatLawTexts(byKeyword, "未在数据库找到包含关键词的条文");
            }
            return "未在数据库找到包含关键词【" + query.keyword + "】的条文，请尝试调整关键词。";
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
        String normalized = normalize(question);
        Matcher lawMatcher = LAW_NAME_PATTERN.matcher(normalized);
        if (!lawMatcher.find()) {
            return null;
        }
        // 截断法律名称，避免将后续“第一章第一条内容”一起吃进匹配导致查询失败
        String rawLawName = lawMatcher.group(0);
        String lawName = trimAfterLawSuffix(rawLawName);

        String chapter = findFirstGroup(normalized, CHAPTER_PATTERN);
        String partOrSection = findFirstGroup(normalized, PART_PATTERN);
        String article = findFirstGroup(normalized, ARTICLE_PATTERN);
        boolean askFull = FULL_PATTERN.matcher(normalized).find();
        boolean general = GENERAL_PATTERN.matcher(normalized).find();

        // 若只有法律名称没有章/条/全文关键词，通常走全文查询；但若问题还带其他描述（如“关于人权”），则视为语义检索而非结构化
        boolean hasStructureKeyword = chapter != null || article != null || partOrSection != null || askFull || general;
        if (!hasStructureKeyword) {
            String remaining = normalized.replaceFirst(Pattern.quote(rawLawName), "");
            if (remaining == null || remaining.isEmpty()) {
                askFull = true; // 仅法名 -> 全文查询
            } else {
                StructuredQuery query = new StructuredQuery();
                query.lawName = lawName;
                query.keyword = extractKeyword(remaining);
                query.askFullLaw = false;
                return query;
            }
        }

        StructuredQuery query = new StructuredQuery();
        query.lawName = lawName;
        query.chapter = chapter == null ? null : "第" + chapter + "章";
        query.article = article == null ? null : "第" + article + "条";
        if (partOrSection != null) {
            query.chapter = "第" + partOrSection + (normalized.contains("节") ? "节" : "编");
        }
        if (general && query.chapter == null) {
            query.chapter = "总则";
        }
        query.askFullLaw = askFull;
        return query;
    }

    private String normalize(String question) {
        // 去除空白和常见书名号/引号，避免干扰匹配
        return question.replaceAll("[\\s《》“”\"']", "");
    }

    private String trimAfterLawSuffix(String rawLawName) {
        if (rawLawName == null) {
            return null;
        }
        // 如果在法典/法/条例后还有“第一章第一条”等尾巴，截断到后缀结束
        String[] suffixes = new String[]{"法典", "条例", "规定", "办法", "规章", "解释", "法"};
        for (String s : suffixes) {
            int idx = rawLawName.indexOf(s);
            if (idx > 0) {
                return rawLawName.substring(0, idx + s.length());
            }
        }
        return rawLawName;
    }

    private String extractKeyword(String text) {
        if (text == null) {
            return null;
        }
        // 去掉常见填充词，保留核心关键词
        String cleaned = text.replaceAll("(关于|相关|的|有哪些|什么|哪几条|哪条|条文|法条|内容|规定|问题|吗|呢|啊)", "");
        cleaned = cleaned.replaceAll("[，。！？?、]", "");
        if (cleaned.isBlank()) {
            return text;
        }
        return cleaned;
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
        String keyword;
        boolean askFullLaw;
    }
}
