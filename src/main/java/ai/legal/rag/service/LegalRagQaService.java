package ai.legal.rag.service;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LegalEmbedding;
import ai.legal.model.LawText;
import ai.legal.rag.prompt.LegalRagPromptBuilder;
import ai.legal.rag.prompt.LegalBasisPromptBuilder;
import ai.legal.rag.prompt.LegalAnalysisPromptBuilder;
import ai.legal.rag.service.ChunkAggregator.AggregatedLawContext;
import ai.legal.service.VectorSearchService;
import ai.legal.util.EmbeddingUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 法律 RAG 问答服务：向量检索 + Prompt 构建 + LLM 调用。
 */
public class LegalRagQaService {

    private static final int TOP_K = 30;
    private static final int MAX_CONTEXTS = 8;
    private static final int SQL_FALLBACK_LIMIT = 80;

    private final VectorSearchService vectorSearchService;
    private final LlmClient llmClient;
    private final LawTextDao lawTextDao;

    public LegalRagQaService(VectorSearchService vectorSearchService, LlmClient llmClient) {
        this(vectorSearchService, llmClient, new LawTextDao());
    }

    public LegalRagQaService(VectorSearchService vectorSearchService, LlmClient llmClient, LawTextDao lawTextDao) {
        this.vectorSearchService = vectorSearchService;
        this.llmClient = llmClient;
        this.lawTextDao = lawTextDao;
    }

    /**
     * 基于 RAG 流程返回问答结果。
     *
     * @param userQuestion 用户问题
     * @return 模型回答
     */
    public String answer(String userQuestion) {
        List<AggregatedLawContext> contexts = retrieveContexts(userQuestion);
        String prompt = LegalRagPromptBuilder.buildPrompt(userQuestion, contexts);
        return llmClient.chat(prompt);
    }

    /**
     * 调试/评测用途：返回本次检索的上下文（不触发 LLM）。
     */
    public List<AggregatedLawContext> debugRetrieveContexts(String userQuestion) {
        return retrieveContexts(userQuestion);
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议。
     */
    public String answerWithReasoning(String userQuestion) {
        return answerWithReasoning(userQuestion, false, "");
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议，可告知事实是否足够。
     */
    public String answerWithReasoning(String userQuestion, boolean factsSufficient) {
        return answerWithReasoning(userQuestion, factsSufficient, "");
    }

    /**
     * 两阶段：先整理法律依据，再做受控法律分析与建议，并传入已确认事实（conversation history 汇总）。
     */
    public String answerWithReasoning(String userQuestion, boolean factsSufficient, String historyFacts) {
        List<AggregatedLawContext> contexts = retrieveContexts(userQuestion);
        // 检索失败兜底：仅在“法条定位/原文”类问题下尝试二次定位，避免通用问题胡乱猜条号
        boolean lawLocatorQuery = looksLikeLawLocatorQuery(userQuestion);
        boolean inconsistent = isInconsistent(userQuestion, contexts);
        if (lawLocatorQuery && (contexts.isEmpty() || inconsistent)) {
            List<AggregatedLawContext> fallback = retryWithSemanticLocator(userQuestion);
            if (!fallback.isEmpty() && !isInconsistent(userQuestion, fallback)) {
                contexts = fallback;
                inconsistent = false;
            }
        }
        if (lawLocatorQuery) {
            if (contexts.isEmpty()) {
                return "未找到条文";
            }
            if (inconsistent) {
                return "检索到的条文与用户请求不一致，已中止回答。";
            }
        }
        // 第一阶段：法律依据
        String legalBasis;
        if (contexts == null || contexts.isEmpty()) {
            legalBasis = "法律依据列表：\n（未检索到与问题直接相关的条文，依据有限）";
        } else {
            String basisPrompt = LegalBasisPromptBuilder.buildPrompt(userQuestion, contexts);
            legalBasis = llmClient.chat(basisPrompt);
        }
        // 第二阶段：受控法律分析
        String analysisPrompt = LegalAnalysisPromptBuilder.buildPrompt(userQuestion, legalBasis, contexts, factsSufficient, historyFacts);
        String analysis = llmClient.chat(analysisPrompt);

        StringBuilder result = new StringBuilder();
        result.append("法律依据：\n").append(legalBasis == null ? "" : legalBasis.trim()).append("\n\n");
        result.append("法律分析与建议：\n").append(analysis == null ? "" : analysis.trim());
        return result.toString();
    }

    private boolean looksLikeLawLocatorQuery(String userQuestion) {
        if (userQuestion == null) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        return normalized.matches(".*(原文|全文|全部内容|全部条文|第[一二三四五六七八九十百千两0-9]+(编|章|节|条)).*");
    }

    private List<AggregatedLawContext> retrieveContexts(String userQuestion) {
        // 1) 向量候选（可能存在噪音）
        List<AggregatedLawContext> vectorContexts = retrieveContextsByVector(userQuestion);
        List<String> keywords = buildKeywordQueryTerms(userQuestion);
        List<ScoredContext> filteredVector = filterAndScoreContexts(vectorContexts, keywords);
        List<AggregatedLawContext> vectorUsable = toContextsIfUsable(filteredVector);
        if (!vectorUsable.isEmpty()) {
            return limitContexts(vectorUsable, MAX_CONTEXTS);
        }

        // 2) SQL 关键词兜底（不依赖 embedding，相对稳定）
        List<AggregatedLawContext> sqlContexts = retrieveContextsBySqlKeywords(userQuestion, keywords);
        if (!sqlContexts.isEmpty()) {
            return limitContexts(sqlContexts, MAX_CONTEXTS);
        }

        // 3) 最终兜底：宁可“依据有限”，也不要胡乱引用
        return List.of();
    }

    private List<AggregatedLawContext> retrieveContextsByVector(String userQuestion) {
        double[] queryVector = EmbeddingUtil.embed(userQuestion);
        List<LegalEmbedding> raw = vectorSearchService.searchTopK(queryVector, TOP_K);
        return ChunkAggregator.aggregate(raw);
    }

    private List<AggregatedLawContext> retrieveContextsBySqlKeywords(String userQuestion, List<String> keywords) {
        if (lawTextDao == null) {
            return List.of();
        }
        List<String> effectiveKeywords = (keywords == null) ? List.of() : keywords;
        List<LawText> candidates = lawTextDao.searchByKeywordsAcrossLaws(effectiveKeywords, SQL_FALLBACK_LIMIT);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredLawText> scored = new ArrayList<>();
        for (LawText item : candidates) {
            if (item == null) {
                continue;
            }
            String combined = normalizeForMatch(joinLawText(item));
            int score = scoreByKeywords(combined, effectiveKeywords);
            if (score > 0) {
                scored.add(new ScoredLawText(item, score));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        scored.sort(Comparator.comparingInt(ScoredLawText::score).reversed()
                .thenComparingLong(s -> s.lawText().getId()));
        // 保守门控：如果最高分都不足，认为兜底检索也不可靠，避免胡乱引用
        if (scored.get(0).score() < 2) {
            return List.of();
        }
        List<AggregatedLawContext> result = new ArrayList<>();
        for (ScoredLawText s : scored) {
            LawText item = s.lawText();
            String label = item.getArticleNumber();
            int articleNo = parseArticleNo(label);
            String content = joinLawText(item);
            result.add(AggregatedLawContext.start(item.getId(), articleNo, label, content));
            if (result.size() >= MAX_CONTEXTS) {
                break;
            }
        }
        return result;
    }

    private String joinLawText(LawText item) {
        StringBuilder sb = new StringBuilder();
        if (item.getLawCode() != null && !item.getLawCode().isBlank()) {
            sb.append(item.getLawCode().trim());
        }
        if (item.getLawTitle() != null && !item.getLawTitle().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(item.getLawTitle().trim());
        }
        if (item.getArticleNumber() != null && !item.getArticleNumber().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(item.getArticleNumber().trim());
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        if (item.getFullText() != null) {
            sb.append(item.getFullText());
        }
        return sb.toString();
    }

    private List<AggregatedLawContext> limitContexts(List<AggregatedLawContext> contexts, int max) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }
        int limit = max <= 0 ? contexts.size() : Math.min(max, contexts.size());
        return contexts.subList(0, limit);
    }

    private List<ScoredContext> filterAndScoreContexts(List<AggregatedLawContext> contexts, List<String> keywords) {
        if (contexts == null || contexts.isEmpty() || keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<ScoredContext> scored = new ArrayList<>();
        for (AggregatedLawContext ctx : contexts) {
            if (ctx == null) {
                continue;
            }
            String content = normalizeForMatch(ctx.getContent());
            int score = scoreByKeywords(content, keywords);
            if (score > 0) {
                scored.add(new ScoredContext(ctx, score));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredContext::score).reversed());
        return scored;
    }

    /**
     * 保守门控：如果命中分数过低，视为不可用，避免“胡乱引用法条”。
     */
    private List<AggregatedLawContext> toContextsIfUsable(List<ScoredContext> scored) {
        if (scored == null || scored.isEmpty()) {
            return List.of();
        }
        int totalScore = 0;
        for (ScoredContext s : scored) {
            totalScore += s.score();
        }
        // 经验阈值：至少两个“有效关键词命中”（或一个较长关键词）
        if (totalScore < 2) {
            return List.of();
        }
        List<AggregatedLawContext> result = new ArrayList<>();
        for (ScoredContext s : scored) {
            result.add(s.context());
            if (result.size() >= MAX_CONTEXTS) {
                break;
            }
        }
        return result;
    }

    private int scoreByKeywords(String content, List<String> keywords) {
        if (content == null || content.isBlank() || keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String c = content;
        int score = 0;
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            String k = kw.trim();
            if (k.length() < 2) {
                continue;
            }
            if (c.contains(k)) {
                score += keywordWeight(k);
            }
        }
        return score;
    }

    private int keywordWeight(String keyword) {
        int len = keyword == null ? 0 : keyword.length();
        if (len >= 4) {
            return 3;
        }
        if (len == 3) {
            return 2;
        }
        return 1;
    }

    private List<String> buildKeywordQueryTerms(String userQuestion) {
        String q = normalizeForMatch(userQuestion);
        if (q.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();

        // 场景化扩展：欠钱/借款
        if (containsAny(q, "欠钱", "欠款", "借钱", "借款", "借条", "欠条", "不还", "逾期", "还款", "利息")) {
            addAll(terms,
                    "借款", "借贷", "出借人", "借款人", "债权", "债务",
                    "欠款", "还款", "逾期", "利息", "借条", "欠条",
                    "履行", "违约", "催告", "诉讼", "起诉", "支付令");
        }

        // 场景化扩展：租赁/房东/押金
        if (containsAny(q, "房东", "租客", "承租", "出租", "租赁", "租房", "押金", "租金", "退租", "解约", "解除")) {
            addAll(terms,
                    "租赁", "租赁合同", "出租人", "承租人",
                    "租金", "押金", "解除", "提前解除",
                    "违约", "赔偿", "损失");
        }

        // 通用：从原问题抽取 2~4 字片段作为关键词补充（过滤停用词）
        List<String> ngrams = extractNgrams(q, 2, 4);
        for (String ng : ngrams) {
            if (isStopKeyword(ng)) {
                continue;
            }
            terms.add(ng);
            if (terms.size() >= 10) {
                break;
            }
        }

        // 最终清洗：去掉过于泛化的词
        terms.removeIf(this::isStopKeyword);
        return terms.stream().limit(10).toList();
    }

    private List<String> extractNgrams(String text, int minLen, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int min = Math.max(2, minLen);
        int max = Math.max(min, maxLen);
        List<String> out = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                segment.append(ch);
            } else {
                flushNgrams(segment, min, max, out);
                segment.setLength(0);
            }
        }
        flushNgrams(segment, min, max, out);
        // 去重并按出现顺序保留
        LinkedHashSet<String> uniq = new LinkedHashSet<>(out);
        return new ArrayList<>(uniq);
    }

    private void flushNgrams(StringBuilder segment, int min, int max, List<String> out) {
        if (segment == null || segment.length() < min) {
            return;
        }
        String s = segment.toString();
        int len = s.length();
        for (int n = max; n >= min; n--) {
            for (int i = 0; i + n <= len; i++) {
                out.add(s.substring(i, i + n));
                if (out.size() >= 50) {
                    return;
                }
            }
        }
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    private boolean isStopKeyword(String kw) {
        if (kw == null) {
            return true;
        }
        String k = kw.trim();
        if (k.isEmpty() || k.length() < 2) {
            return true;
        }
        // 过于泛化/噪音词
        return containsAny(k, "怎么办", "如何", "怎么", "是否", "能否", "可否", "请问", "问题", "法律", "法条", "依据", "内容", "原文", "全文")
                || k.matches("第[一二三四五六七八九十百千两0-9]+(编|章|节|条)")
                || k.matches("[0-9]+");
    }

    private String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        // 仅用于包含判断：去空白、转小写、保留中英文数字
        String lowered = text.toLowerCase(Locale.ROOT);
        return lowered.replaceAll("\\s+", "");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private void addAll(LinkedHashSet<String> set, String... items) {
        if (set == null || items == null) {
            return;
        }
        for (String it : items) {
            if (it != null && !it.isBlank()) {
                set.add(it.trim());
            }
        }
    }

    private int parseArticleNo(String articleNo) {
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

    /**
     * 检查检索结果与用户问题中的法名/条号是否一致。
     */
    private boolean isInconsistent(String userQuestion, List<AggregatedLawContext> contexts) {
        if (userQuestion == null || contexts == null || contexts.isEmpty()) {
            return false;
        }
        String normalized = userQuestion.replaceAll("\\s+", "");
        boolean asksArticle = normalized.matches(".*第[一二三四五六七八九十百0-9]+条.*");
        boolean asksLawName = normalized.contains("法典") || normalized.contains("法") || normalized.contains("条例");
        if (!asksArticle && !asksLawName) {
            return false;
        }
        // 若用户提到条号，要求返回的条文范围包含该条，否则视为不一致
        if (asksArticle) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("第([一二三四五六七八九十百0-9]+)条").matcher(normalized);
            String target = null;
            if (m.find()) {
                target = m.group(1);
            }
            if (target != null) {
                for (AggregatedLawContext ctx : contexts) {
                    if (ctx.getArticleRange() != null && ctx.getArticleRange().contains(target)) {
                        return false; // 找到匹配
                    }
                }
                return true; // 未匹配到用户请求条号
            }
        }
        return false;
    }

    /**
     * 使用 LLM 进行语义定位条号，再尝试结构化查询。
     */
    private List<AggregatedLawContext> retryWithSemanticLocator(String userQuestion) {
        try {
            // 简单提示让 LLM 猜测可能的编/章/条
            String locatorPrompt = "根据用户问题，推测可能的法律编/章/条号，只输出类似“第一编 第一章 第一条”或“第九条”的简短结果：\n" + userQuestion;
            String locator = llmClient.chat(locatorPrompt);
            if (locator == null || locator.isBlank()) {
                return List.of();
            }
            // 将定位结果拼回去做结构化检索
            String combined = userQuestion + " " + locator.trim();
            List<LegalEmbedding> raw = vectorSearchService.searchTopK(EmbeddingUtil.embed(combined), TOP_K);
            return ChunkAggregator.aggregate(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    private record ScoredContext(AggregatedLawContext context, int score) {
    }

    private record ScoredLawText(LawText lawText, int score) {
    }
}
