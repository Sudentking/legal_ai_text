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
import java.util.Scanner;

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
     * 命令行入口：支持 args 文件路径，也支持交互式多次导入。
     */
    public static void main(String[] args) {
        LawDocumentImportService service = new LawDocumentImportService();
        // 优先处理传入的参数，方便脚本化调用
        if (args.length > 0) {
            service.importFromFile(args[0]);
            return;
        }
        // 交互式导入，持续等待用户输入
        System.out.println("法律文档导入入口已启动。");
        System.out.println("请输入要导入的法律文档路径（Word/PDF/TXT），输入 exit/quit 退出：");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine();
                if (line == null) {
                    continue;
                }
                String path = line.trim();
                if (path.equalsIgnoreCase("exit") || path.equalsIgnoreCase("quit")) {
                    System.out.println("已退出。");
                    break;
                }
                if (path.isEmpty()) {
                    continue;
                }
                File f = new File(path);
                String name = f.getName().toLowerCase();
                if (!(name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".txt"))) {
                    System.err.println("不支持的文件格式，仅支持 .docx/.pdf/.txt");
                    continue;
                }
                service.importFromFile(path);
            }
        }
    }
}
