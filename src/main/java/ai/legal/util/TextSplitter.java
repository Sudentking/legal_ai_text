package ai.legal.util;

import ai.legal.model.LawText;
import ai.legal.model.LawTextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切分工具，将长文本按指定长度分片。
 */
public class TextSplitter {

    private static final int MIN_CHUNK_SIZE = 300;
    private static final int MAX_CHUNK_SIZE = 500;

    private TextSplitter() {
    }

    /**
     * 将文本切分为指定大小的片段。
     *
     * @param text      原始文本
     * @param chunkSize 目标分片长度，自动限制在 300-500 范围内
     * @return 片段列表
     */
    public static List<String> splitToStrings(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int size = Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSize));
        String[] paragraphs = text.split("\\r?\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph == null) {
                continue;
            }
            String part = paragraph.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(part);
            if (current.length() >= size) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * 基于 LawText 生成切片对象列表，不填充 vector_id。
     *
     * @param lawText   原始条文
     * @param chunkSize 目标分片长度
     * @return LawTextChunk 列表（仅含 chunkText 与顺序）
     */
    public static List<LawTextChunk> splitToChunks(LawText lawText, int chunkSize) {
        List<LawTextChunk> result = new ArrayList<>();
        if (lawText == null) {
            return result;
        }
        List<String> texts = splitToStrings(lawText.getFullText(), chunkSize);
        for (int i = 0; i < texts.size(); i++) {
            LawTextChunk chunk = new LawTextChunk();
            chunk.setDocumentId(lawText.getId());
            chunk.setChunkText(texts.get(i));
            chunk.setChunkOrder(i);
            result.add(chunk);
        }
        return result;
    }
}
