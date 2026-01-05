package ai.legal.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律条文编号解析工具：支持中文数字与阿拉伯数字混用。
 */
public final class LawNumberingUtil {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("第([一二三四五六七八九十百千两〇零0-9]+)条");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第([一二三四五六七八九十百千两〇零0-9]+)章");

    private LawNumberingUtil() {
    }

    public static Integer parseArticleNo(String articleNumber) {
        if (articleNumber == null || articleNumber.isBlank()) {
            return null;
        }
        Matcher m = ARTICLE_PATTERN.matcher(articleNumber.replaceAll("\\s+", ""));
        if (m.find()) {
            return parseNumberToken(m.group(1));
        }
        // 兜底：直接解析纯数字
        String digits = articleNumber.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Integer parseChapterNoFromTitle(String lawTitle) {
        if (lawTitle == null || lawTitle.isBlank()) {
            return null;
        }
        Matcher m = CHAPTER_PATTERN.matcher(lawTitle.replaceAll("\\s+", ""));
        if (m.find()) {
            return parseNumberToken(m.group(1));
        }
        return null;
    }

    private static Integer parseNumberToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.trim();
        if (t.matches("\\d+")) {
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return toArabicNumber(t);
    }

    /**
     * 将中文数字（含“十/百/千/两/〇/零”）转换为阿拉伯数字。
     */
    public static Integer toArabicNumber(String chinese) {
        if (chinese == null || chinese.isBlank()) {
            return null;
        }
        String s = chinese.replace("〇", "零").replace("两", "二").trim();
        int result = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Integer digit = digitOf(c);
            if (digit != null) {
                number = digit;
                continue;
            }
            int unit = unitOf(c);
            if (unit > 0) {
                if (number == 0 && (c == '十' || c == '百' || c == '千')) {
                    // “十二”=“一十二”
                    number = 1;
                }
                section += number * unit;
                number = 0;
                continue;
            }
            // 非数字非单位：忽略
        }
        result += section + number;
        return result <= 0 ? null : result;
    }

    private static Integer digitOf(char c) {
        return switch (c) {
            case '零' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> null;
        };
    }

    private static int unitOf(char c) {
        return switch (c) {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            default -> 0;
        };
    }
}

