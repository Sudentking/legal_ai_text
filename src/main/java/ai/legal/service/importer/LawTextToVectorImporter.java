package ai.legal.service.importer;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.model.LawText;
import ai.legal.service.VectorSearchService;

import java.util.List;

/**
 * 将法律条文批量导入向量库与切片表的入口。
 */
public class LawTextToVectorImporter {

    /**
     * 批量导入主流程。
     *
     * @param args 启动参数（无需传入）
     */
    public static void main(String[] args) {
        ImportPolicy policy = ImportPolicy.defaultPolicy();
        LawTextDao lawTextDao = new LawTextDao();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LawTextChunkService chunkService = new LawTextChunkService(vectorSearchService, lawTextDao);

        long start = System.currentTimeMillis();
        List<LawText> lawTexts = policy.isIncremental() ? lawTextDao.findUnprocessed() : lawTextDao.findAll();
        System.out.println("待处理条文数量: " + lawTexts.size());

        ImportResult importResult = new ImportResult();
        for (LawText lawText : lawTexts) {
            LawTextProcessResult result = chunkService.processLawText(lawText, policy);
            importResult.add(result);
        }

        long costMs = System.currentTimeMillis() - start;
        importResult.setElapsedMs(costMs);
        System.out.println("导入完成，处理总数: " + importResult.getTotal()
                + "，成功: " + importResult.getSuccess()
                + "，失败: " + importResult.getFailed()
                + "，跳过: " + importResult.getSkipped()
                + "，耗时 " + importResult.getElapsedMs() + " ms");
        if (!importResult.getFailedIds().isEmpty()) {
            System.out.println("失败条文ID: " + importResult.getFailedIds());
        }
        if (!importResult.getSkippedIds().isEmpty()) {
            System.out.println("跳过条文ID: " + importResult.getSkippedIds());
        }
    }
}
