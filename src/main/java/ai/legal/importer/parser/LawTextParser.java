package ai.legal.importer.parser;

import ai.legal.model.LawText;

import java.io.File;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律条文解析器：从全文文本解析出 LawText 列表。
 */
public class LawTextParser {

    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第[一二三四五六七八九十百0-9]+章\\s*(.*)");
    // 捕获“第X条”作为组1，避免将后续标题/正文放入条号
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("^(第[一二三四五六七八九十百0-9]+条)");

    /**
     * 将全文解析为 LawText 列表。
     *
     * @param fullText 全文
     * @param source   文件源（用于生成 lawCode / lawTitle）
     * @return LawText 列表
     */
    public List<LawText> parse(String fullText, File source) {
        List<LawText> result = new ArrayList<>();
        if (fullText == null || fullText.isEmpty()) {
            return result;
        }
        String[] lines = fullText.split("\\r?\\n");
        String lawTitle = deriveLawTitle(source);
        String lawCode = lawTitle;
        String currentChapter = null;

        String currentArticleNo = null;
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher chapterMatcher = CHAPTER_PATTERN.matcher(line);
            if (chapterMatcher.matches()) {
                currentChapter = line;
                continue;
            }
            Matcher articleMatcher = ARTICLE_PATTERN.matcher(line);
            if (articleMatcher.find()) {
                // 结束上一条
                if (currentArticleNo != null && currentContent.length() > 0) {
                    result.add(buildLawText(lawCode, lawTitle, currentChapter, currentArticleNo, currentContent.toString()));
                }
                // 开始新条
                currentArticleNo = articleMatcher.group(1); // 只取“第X条”，避免正文溢出
                currentContent.setLength(0);
                currentContent.append(line).append("\n");
            } else {
                if (currentContent.length() > 0) {
                    currentContent.append(line).append("\n");
                }
            }
        }
        // 收尾
        if (currentArticleNo != null && currentContent.length() > 0) {
            result.add(buildLawText(lawCode, lawTitle, currentChapter, currentArticleNo, currentContent.toString()));
        }
        return result;
    }

    private LawText buildLawText(String lawCode, String lawTitle, String chapterTitle, String articleNo, String content) {
        String title = chapterTitle == null ? lawTitle : lawTitle + " " + chapterTitle;
        LawText lawText = new LawText();
        lawText.setLawCode(lawCode);
        lawText.setLawTitle(title);
        // 遵循表结构 article_number VARCHAR(50)，截断过长的条号，防止入库失败
        if (articleNo != null && articleNo.length() > 50) {
            articleNo = articleNo.substring(0, 50);
        }
        lawText.setArticleNumber(articleNo);
        lawText.setFullText(content);
        lawText.setEffectiveDate((Date) null);
        return lawText;
    }

    private String deriveLawTitle(File source) {
        if (source == null) {
            return "法律文件";
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }
}
