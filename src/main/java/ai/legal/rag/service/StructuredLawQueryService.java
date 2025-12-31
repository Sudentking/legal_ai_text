package ai.legal.rag.service;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 针对“明确法律名称 + 章/条/全文”类问题的快速路由：
 * 命中后直接从 MySQL 返回原文，跳过向量检索，避免检索误差导致拒答。
 */
public class StructuredLawQueryService {

    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9_·]{2,60}?(法典|法|条例|规定|办法|规章|解释))");
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
            List<LawText> byArticle = lawTextDao.findByLawNameAndArticle(query.lawName, query.article, query.articleAlt);
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
        if (query.chapter != null || query.partOrSection != null) {
            List<LawText> byChapter;
            // 同时包含“第X编 + 第X章”时必须双条件命中，避免把其他编的“第一章”等混入
            if (query.chapter != null && query.partOrSection != null) {
                byChapter = lawTextDao.findByLawNameAndTwoTitleKeywords(
                        query.lawName,
                        query.partOrSection,
                        query.partOrSectionAlt,
                        query.chapter,
                        query.chapterAlt
                );
                // 若为“编+章”查询，尝试按下一章边界补全缺失条文（例如第十二条缺失但第十三条已在第二章）
                if (!byChapter.isEmpty() && query.chapterNo != null && query.partOrSection.endsWith("编")) {
                    byChapter = expandByChapterRange(query, byChapter);
                }
            } else {
                String kw1 = query.chapter != null ? query.chapter : query.partOrSection;
                String kw2 = query.chapter != null ? query.chapterAlt : query.partOrSectionAlt;
                byChapter = lawTextDao.findByLawNameAndChapter(query.lawName, kw1, kw2);
            }
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

        NumberToken chapterToken = normalizeNumberToken(findFirstGroup(normalized, CHAPTER_PATTERN));
        NumberToken articleToken = normalizeNumberToken(findFirstGroup(normalized, ARTICLE_PATTERN));
        PartToken partToken = findPartToken(normalized);
        boolean askFull = FULL_PATTERN.matcher(normalized).find();
        boolean general = GENERAL_PATTERN.matcher(normalized).find();

        // 若只有法律名称没有章/条/全文关键词，通常走全文查询；但若问题还带其他描述（如“关于人权”），则视为语义检索而非结构化
        boolean hasStructureKeyword = chapterToken != null || articleToken != null || partToken != null || askFull || general;
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
        if (chapterToken != null) {
            query.chapter = "第" + chapterToken.chinese + "章";
            query.chapterAlt = chapterToken.arabic == null ? null : ("第" + chapterToken.arabic + "章");
            if (chapterToken.arabic != null) {
                int no = parseSafeInt(chapterToken.arabic);
                query.chapterNo = no > 0 ? no : null;
            }
        }
        if (articleToken != null) {
            query.article = "第" + articleToken.chinese + "条";
            query.articleAlt = articleToken.arabic == null ? null : ("第" + articleToken.arabic + "条");
        }
        if (partToken != null) {
            query.partOrSection = "第" + partToken.number.chinese + partToken.unit;
            query.partOrSectionAlt = partToken.number.arabic == null ? null : ("第" + partToken.number.arabic + partToken.unit);
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

    private PartToken findPartToken(String text) {
        Matcher m = PART_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        String num = m.group(1);
        String unit = m.group(2);
        NumberToken token = normalizeNumberToken(num);
        if (token == null) {
            return null;
        }
        PartToken pt = new PartToken();
        pt.number = token;
        pt.unit = unit;
        return pt;
    }

    private NumberToken normalizeNumberToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.trim();
        NumberToken nt = new NumberToken();
        if (t.matches("\\d+")) {
            nt.arabic = t;
            nt.chinese = toChineseNumber(parseSafeInt(t));
            return nt;
        }
        nt.chinese = t;
        Integer arabic = toArabicNumber(t);
        nt.arabic = arabic == null ? null : String.valueOf(arabic);
        return nt;
    }

    private int parseSafeInt(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String toChineseNumber(int n) {
        if (n <= 0) {
            return String.valueOf(n);
        }
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        String[] units = {"", "十", "百", "千"};
        int[] parts = {n / 1000, (n % 1000) / 100, (n % 100) / 10, n % 10};
        StringBuilder sb = new StringBuilder();
        boolean zeroPending = false;
        for (int i = 0; i < parts.length; i++) {
            int val = parts[i];
            int unitIdx = parts.length - 1 - i;
            if (val == 0) {
                zeroPending = sb.length() > 0;
                continue;
            }
            if (zeroPending) {
                sb.append(digits[0]);
                zeroPending = false;
            }
            // 10~19: “十X” 而不是 “一十X”
            if (unitIdx == 1 && val == 1 && sb.length() == 0) {
                sb.append(units[unitIdx]);
            } else {
                sb.append(digits[val]).append(units[unitIdx]);
            }
        }
        return sb.length() == 0 ? digits[0] : sb.toString();
    }

    private Integer toArabicNumber(String chinese) {
        if (chinese == null || chinese.isBlank()) {
            return null;
        }
        int result = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < chinese.length(); i++) {
            int val = chineseDigitValue(chinese.charAt(i));
            if (val >= 0) {
                number = val;
                continue;
            }
            int unit = chineseUnitValue(chinese.charAt(i));
            if (unit == 0) {
                return null;
            }
            if (unit == 10 && number == 0) {
                number = 1; // “十” = 10，“十二” = 12
            }
            section += number * unit;
            number = 0;
        }
        return result + section + number;
    }

    private int chineseDigitValue(char c) {
        return switch (c) {
            case '零', '〇' -> 0;
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private int chineseUnitValue(char c) {
        return switch (c) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            default -> 0;
        };
    }

    private String formatLawTexts(List<LawText> lawTexts, String emptyMessage) {
        if (lawTexts == null || lawTexts.isEmpty()) {
            return emptyMessage;
        }
        StringBuilder sb = new StringBuilder();
        List<LawText> sorted = sortByArticleNumber(lawTexts);
        for (LawText lt : sorted) {
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
        String chapterAlt;
        Integer chapterNo;
        String article;
        String articleAlt;
        String partOrSection;
        String partOrSectionAlt;
        String keyword;
        boolean askFullLaw;
    }

    private static class NumberToken {
        String chinese;
        String arabic;
    }

    private static class PartToken {
        NumberToken number;
        String unit;
    }

    private List<LawText> expandByChapterRange(StructuredQuery query, List<LawText> currentChapter) {
        Integer start = minArticleNo(currentChapter);
        if (start == null) {
            return currentChapter;
        }
        Integer nextChapterNo = query.chapterNo == null ? null : query.chapterNo + 1;
        if (nextChapterNo == null || nextChapterNo <= 0) {
            return currentChapter;
        }
        String nextChapterChinese = "第" + toChineseNumber(nextChapterNo) + "章";
        String nextChapterArabic = "第" + nextChapterNo + "章";
        List<LawText> nextChapter = lawTextDao.findByLawNameAndTwoTitleKeywords(
                query.lawName,
                query.partOrSection,
                query.partOrSectionAlt,
                nextChapterChinese,
                nextChapterArabic
        );
        Integer boundary = minArticleNo(nextChapter);
        if (boundary == null || boundary <= start) {
            return currentChapter;
        }
        List<LawText> inPart = lawTextDao.findByLawNameAndChapter(query.lawName, query.partOrSection, query.partOrSectionAlt);
        if (inPart.isEmpty()) {
            return currentChapter;
        }
        List<LawText> filtered = new ArrayList<>();
        for (LawText lt : inPart) {
            Integer art = parseArticleNoFromArticleNumber(lt == null ? null : lt.getArticleNumber());
            if (art != null && art >= start && art < boundary) {
                filtered.add(lt);
            }
        }
        return filtered.isEmpty() ? currentChapter : filtered;
    }

    private List<LawText> sortByArticleNumber(List<LawText> lawTexts) {
        List<LawText> copy = new ArrayList<>(lawTexts);
        copy.sort(Comparator.comparingInt(o -> {
            Integer art = parseArticleNoFromArticleNumber(o == null ? null : o.getArticleNumber());
            return art == null ? Integer.MAX_VALUE : art;
        }));
        return copy;
    }

    private Integer minArticleNo(List<LawText> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Integer min = null;
        for (LawText lt : list) {
            Integer art = parseArticleNoFromArticleNumber(lt == null ? null : lt.getArticleNumber());
            if (art == null) {
                continue;
            }
            if (min == null || art < min) {
                min = art;
            }
        }
        return min;
    }

    private Integer parseArticleNoFromArticleNumber(String articleNumber) {
        if (articleNumber == null || articleNumber.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("第([一二三四五六七八九十百千两0-9]+)条").matcher(articleNumber.replaceAll("\\s+", ""));
        if (!m.find()) {
            return null;
        }
        String token = m.group(1);
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.matches("\\d+")) {
            int n = parseSafeInt(token);
            return n > 0 ? n : null;
        }
        Integer n = toArabicNumber(token);
        return n != null && n > 0 ? n : null;
    }
}
