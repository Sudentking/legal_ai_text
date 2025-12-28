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
        LawTextDao lawTextDao = new LawTextDao();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LawTextChunkService chunkService = new LawTextChunkService(vectorSearchService, lawTextDao);

        List<LawText> lawTexts = lawTextDao.findAll();
        System.out.println("待处理条文数量: " + lawTexts.size());

        for (LawText lawText : lawTexts) {
            chunkService.processLawText(lawText);
        }

        System.out.println("导入完成，处理总数: " + lawTexts.size());
    }
}
