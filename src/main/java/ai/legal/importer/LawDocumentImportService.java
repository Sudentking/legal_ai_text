package ai.legal.importer;

import ai.legal.dao.mysql.LawTextDao;
import ai.legal.importer.loader.DocumentLoader;
import ai.legal.importer.loader.DocumentLoaderFactory;
import ai.legal.importer.parser.LawTextParser;
import ai.legal.model.LawText;
import ai.legal.service.importer.LawTextChunkService;
import ai.legal.service.VectorSearchService;
import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.service.importer.ImportPolicy;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 法律法规导入模块：从 Word/PDF/TXT 读取 → 解析条文 → 写入 MySQL → 切分 + 向量入库。
 */
public class LawDocumentImportService {

    private final LawTextDao lawTextDao;
    private final LawTextParser parser;
    private final LawTextChunkService chunkService;

    public LawDocumentImportService() {
        this.lawTextDao = new LawTextDao();
        this.parser = new LawTextParser();
        this.chunkService = new LawTextChunkService(new VectorSearchService(new LegalEmbeddingDao()), lawTextDao);
    }

    /**
     * 导入指定文件。
     *
     * @param path 文件路径（支持 .docx/.pdf/.txt）
     */
    public void importFromFile(String path) {
        long start = System.currentTimeMillis();
        File file = new File(path);
        if (!file.exists()) {
            System.err.println("文件不存在: " + path);
            return;
        }
        try {
            DocumentLoader loader = DocumentLoaderFactory.forFile(file);
            String text = loader.load(file);
            List<LawText> lawTexts = parser.parse(text, file);
            int success = 0;
            int failed = 0;
            ImportPolicy policy = ImportPolicy.defaultPolicy();
            for (LawText lawText : lawTexts) {
                long id = lawTextDao.insertLawText(lawText);
                if (id <= 0) {
                    failed++;
                    continue;
                }
                lawText.setId(id);
                chunkService.processLawText(lawText, policy);
                success++;
            }
            long cost = System.currentTimeMillis() - start;
            System.out.println("导入完成：成功 " + success + " 条，失败 " + failed + " 条，耗时 " + cost + " ms");
        } catch (IOException e) {
            System.err.println("导入失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 命令行入口示例。
     *
     * @param args 参数：文件路径
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.importer.LawDocumentImportService <文件路径>");
            return;
        }
        new LawDocumentImportService().importFromFile(args[0]);
    }
}
