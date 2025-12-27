package ai.legal;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.model.LegalEmbedding;
import ai.legal.service.VectorSearchService;

import java.sql.SQLException;
import java.util.List;

/**
 * 程序入口：插入一条示例数据并执行向量相似度查询。
 */
public class App {

    private static final int VECTOR_DIMENSION = 1536;

    public static void main(String[] args) {
        // 初始化服务层
        VectorSearchService service = new VectorSearchService(new LegalEmbeddingDao());
        try {
            // 构造示例向量与文本
            LegalEmbedding sample = new LegalEmbedding(
                    1001L,
                    "Article-1",
                    0,
                    buildDemoVector(0.01),
                    "示例法律条款内容片段，用于演示向量插入与检索。",
                    "demo-source"
            );

            // 插入数据
            long newId = service.insertEmbedding(sample);
            System.out.println("插入成功，ID=" + newId);

            // 构造查询向量并执行相似度查询
            double[] queryVector = buildDemoVector(0.02);
            List<LegalEmbedding> results = service.searchSimilar(queryVector, 3);

            System.out.println("Top-3 相似结果：");
            for (LegalEmbedding item : results) {
                printEmbedding(item);
            }
        } catch (SQLException e) {
            System.err.println("数据库操作失败: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 构造固定模式的示例向量，方便快速演示
    private static double[] buildDemoVector(double baseValue) {
        double[] vector = new double[VECTOR_DIMENSION];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = baseValue + (i % 10) * 0.0001;
        }
        return vector;
    }

    // 将查询结果简洁打印到控制台
    private static void printEmbedding(LegalEmbedding embedding) {
        System.out.println("----------------------------");
        System.out.println("ID: " + embedding.getId());
        System.out.println("law_id: " + embedding.getLawId());
        System.out.println("article_no: " + embedding.getArticleNo());
        System.out.println("chunk_index: " + embedding.getChunkIndex());
        System.out.println("source: " + embedding.getSource());
        System.out.println("created_at: " + embedding.getCreatedAt());
        System.out.println("content: " + embedding.getContent());
    }
}
