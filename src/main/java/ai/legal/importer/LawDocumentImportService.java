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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

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
        LawDocumentImportFileResult result = importFromFileDetailed(path, ImportPolicy.defaultPolicy());
        if (result.isSuccess()) {
            System.out.println("导入完成：成功 " + result.getInsertedLawTexts()
                    + " 条，失败 " + result.getFailedLawTexts()
                    + " 条，耗时 " + result.getElapsedMs() + " ms");
        } else if (result.isSkipped()) {
            System.out.println("跳过导入: " + (result.getMessage() == null ? "" : result.getMessage()));
        } else {
            System.err.println("导入失败: " + (result.getMessage() == null ? "" : result.getMessage()));
        }
    }

    /**
     * 导入单个文件并返回结构化结果（适配后续 Web/API）。
     */
    public LawDocumentImportFileResult importFromFileDetailed(String path, ImportPolicy policy) {
        long start = System.currentTimeMillis();
        if (path == null || path.isBlank()) {
            return LawDocumentImportFileResult.fail(path, "文件路径为空");
        }
        File file = new File(path);
        if (!file.exists()) {
            return LawDocumentImportFileResult.fail(path, "文件不存在");
        }
        if (file.isDirectory()) {
            return LawDocumentImportFileResult.fail(path, "路径为目录，请使用批量导入");
        }
        if (!isSupported(file)) {
            return LawDocumentImportFileResult.skipped(path, "不支持的文件格式，仅支持 .docx/.pdf/.txt");
        }

        try {
            DocumentLoader loader = DocumentLoaderFactory.forFile(file);
            String text = loader.load(file);
            List<LawText> lawTexts = parser.parse(text, file);

            int inserted = 0;
            int failed = 0;
            ImportPolicy effectivePolicy = policy == null ? ImportPolicy.defaultPolicy() : policy;

            for (LawText lawText : lawTexts) {
                long id = lawTextDao.insertLawText(lawText);
                if (id <= 0) {
                    failed++;
                    continue;
                }
                lawText.setId(id);
                chunkService.processLawText(lawText, effectivePolicy);
                inserted++;
            }

            LawDocumentImportFileResult result = LawDocumentImportFileResult.success(file.getAbsolutePath());
            result.setParsedLawTexts(lawTexts.size());
            result.setInsertedLawTexts(inserted);
            result.setFailedLawTexts(failed);
            result.setElapsedMs(System.currentTimeMillis() - start);
            result.setMessage("ok");
            return result;
        } catch (IOException e) {
            LawDocumentImportFileResult result = LawDocumentImportFileResult.fail(file.getAbsolutePath(), e.getMessage());
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            LawDocumentImportFileResult result = LawDocumentImportFileResult.fail(file.getAbsolutePath(), e.getMessage());
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    /**
     * 批量导入：支持多个文件路径/目录路径；返回结构化结果。
     */
    public LawDocumentImportBatchResult importBatch(LawDocumentBatchImportRequest request) {
        long start = System.currentTimeMillis();
        LawDocumentImportBatchResult batchResult = new LawDocumentImportBatchResult();
        if (request == null || request.getPaths().isEmpty()) {
            batchResult.setElapsedMs(0);
            return batchResult;
        }
        ImportPolicy policy = request.toPolicy();

        Set<String> uniqueFiles = new LinkedHashSet<>();
        for (String p : request.getPaths()) {
            if (p == null || p.isBlank()) {
                continue;
            }
            File target = new File(p.trim());
            if (!target.exists()) {
                batchResult.addFileResult(LawDocumentImportFileResult.fail(p, "路径不存在"));
                continue;
            }
            if (target.isFile()) {
                if (!isSupported(target)) {
                    batchResult.addFileResult(LawDocumentImportFileResult.skipped(p, "不支持的文件格式，仅支持 .docx/.pdf/.txt"));
                    continue;
                }
                uniqueFiles.add(target.getAbsolutePath());
                continue;
            }
            if (target.isDirectory()) {
                List<File> dirFiles;
                try {
                    dirFiles = listSupportedFiles(target.toPath(), request.isRecursive());
                } catch (IOException e) {
                    batchResult.addFileResult(LawDocumentImportFileResult.fail(target.getAbsolutePath(), "扫描目录失败: " + e.getMessage()));
                    continue;
                }
                if (dirFiles.isEmpty()) {
                    batchResult.addFileResult(LawDocumentImportFileResult.skipped(target.getAbsolutePath(), "目录中未找到可导入文件"));
                    continue;
                }
                for (File f : dirFiles) {
                    uniqueFiles.add(f.getAbsolutePath());
                }
            }
        }

        List<String> ordered = new ArrayList<>(uniqueFiles);
        ordered.sort(String::compareTo);
        for (String filePath : ordered) {
            LawDocumentImportFileResult r = importFromFileDetailed(filePath, policy);
            batchResult.addFileResult(r);
        }
        batchResult.setElapsedMs(System.currentTimeMillis() - start);
        return batchResult;
    }

    private List<File> listSupportedFiles(Path dir, boolean recursive) throws IOException {
        List<File> files = new ArrayList<>();
        if (dir == null) {
            return files;
        }
        try (Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(this::isSupported)
                    .sorted(Comparator.comparing(File::getAbsolutePath))
                    .forEach(files::add);
        }
        return files;
    }

    private boolean isSupported(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".txt");
    }

    /**
     * 命令行入口：支持 args 文件路径，也支持交互式多次导入。
     */
    public static void main(String[] args) {
        LawDocumentImportService service = new LawDocumentImportService();
        // 优先处理传入的参数，方便脚本化调用（支持多文件/目录批量导入）
        if (args.length > 0) {
            // 兼容旧用法：仅一个文件参数时仍走单文件导入
            if (args.length == 1) {
                File f = new File(args[0]);
                if (f.isFile()) {
                    service.importFromFile(args[0]);
                    return;
                }
            }
            LawDocumentBatchImportRequest request = new LawDocumentBatchImportRequest(List.of(args));
            request.setRecursive(true);
            LawDocumentImportBatchResult result = service.importBatch(request);
            System.out.println("批量导入完成：文件总数=" + result.getTotalFiles()
                    + "，成功文件=" + result.getSuccessFiles()
                    + "，失败文件=" + result.getFailedFiles()
                    + "，跳过文件=" + result.getSkippedFiles()
                    + "，解析条文=" + result.getParsedLawTexts()
                    + "，入库成功=" + result.getInsertedLawTexts()
                    + "，入库失败=" + result.getFailedLawTexts()
                    + "，耗时=" + result.getElapsedMs() + "ms");
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
                if (f.isDirectory()) {
                    LawDocumentBatchImportRequest request = new LawDocumentBatchImportRequest(List.of(path));
                    request.setRecursive(true);
                    LawDocumentImportBatchResult result = service.importBatch(request);
                    System.out.println("批量导入完成：文件总数=" + result.getTotalFiles()
                            + "，成功文件=" + result.getSuccessFiles()
                            + "，失败文件=" + result.getFailedFiles()
                            + "，跳过文件=" + result.getSkippedFiles()
                            + "，解析条文=" + result.getParsedLawTexts()
                            + "，入库成功=" + result.getInsertedLawTexts()
                            + "，入库失败=" + result.getFailedLawTexts()
                            + "，耗时=" + result.getElapsedMs() + "ms");
                    continue;
                }
                service.importFromFile(path);
            }
        }
    }
}
