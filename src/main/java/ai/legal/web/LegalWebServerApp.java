package ai.legal.web;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.AgentTaskHistoryDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.dao.mysql.QaLogDao;
import ai.legal.dao.mysql.UserAccountDao;
import ai.legal.dao.mysql.UserPermissionDao;
import ai.legal.dao.mysql.UserSessionDao;
import ai.legal.importer.LawDocumentImportService;
import ai.legal.rag.agent.LegalAgentService;
import ai.legal.rag.intent.LegalIntentClassifier;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.rag.service.StructuredLawQueryService;
import ai.legal.service.VectorSearchService;
import ai.legal.service.auth.AdminService;
import ai.legal.service.auth.AuthService;
import ai.legal.service.kb.KnowledgeBaseMaintenanceService;
import ai.legal.service.kb.KnowledgeBaseQualityService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Web 雏形入口（纯 JDK HttpServer，无额外框架依赖）。
 *
 * <p>包含：
 * - 登录/注册
 * - 普通用户提问页（仅可问 Agent）
 * - 超级用户后台（日志 + 批量导入）
 */
public class LegalWebServerApp {

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);

        LlmClient llmClient = createLlmClient();
        LegalEmbeddingDao embeddingDao = new LegalEmbeddingDao();
        VectorSearchService vectorSearchService = new VectorSearchService(embeddingDao);
        LegalRagQaService ragQaService = new LegalRagQaService(vectorSearchService, llmClient);
        LawTextDao lawTextDao = new LawTextDao();
        StructuredLawQueryService structuredLawQueryService = new StructuredLawQueryService(lawTextDao);
        LegalAgentService agentService = new LegalAgentService(new LegalIntentClassifier(), ragQaService, structuredLawQueryService, llmClient);

        UserAccountDao userAccountDao = new UserAccountDao();
        UserPermissionDao userPermissionDao = new UserPermissionDao();
        UserSessionDao userSessionDao = new UserSessionDao();
        AuthService authService = new AuthService(userAccountDao, userSessionDao, userPermissionDao);
        AdminService adminService = new AdminService(userAccountDao, userPermissionDao);
        LawDocumentImportService importService = new LawDocumentImportService();

        QaLogDao qaLogDao = new QaLogDao();
        AgentTaskHistoryDao taskHistoryDao = new AgentTaskHistoryDao();

        KnowledgeBaseMaintenanceService kbMaintenanceService = new KnowledgeBaseMaintenanceService(lawTextDao, embeddingDao);
        KnowledgeBaseQualityService kbQualityService = new KnowledgeBaseQualityService(lawTextDao);

        WebAppContext ctx = new WebAppContext(
                authService,
                adminService,
                agentService,
                importService,
                lawTextDao,
                kbMaintenanceService,
                kbQualityService,
                qaLogDao,
                taskHistoryDao
        );

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // 轻量线程池即可（JDBC/向量检索可能阻塞，避免单线程卡死）
        server.setExecutor(Executors.newFixedThreadPool(16));

        LegalWebHandlers.register(server, ctx);

        server.start();
        System.out.println("Legal Web Server started: http://localhost:" + port);
        System.out.println("Pages: /login /register /app /admin");
    }

    private static int resolvePort(String[] args) {
        String fromArgs = (args != null && args.length > 0) ? args[0] : null;
        String fromProp = System.getProperty("web.port");
        String fromEnv = System.getenv("WEB_PORT");
        String raw = WebUtil.firstNonBlank(fromArgs, WebUtil.firstNonBlank(fromProp, fromEnv));
        int port = WebUtil.parseInt(raw, 8080);
        if (port <= 0 || port > 65535) {
            return 8080;
        }
        return port;
    }

    private static LlmClient createLlmClient() {
        try {
            return new DeepSeekLlmClient();
        } catch (Exception e) {
            System.out.println("警告：DeepSeek 未配置或不可用，使用 Mock LLM。原因: " + e.getMessage());
            return prompt -> "MOCK_LLM_REPLY: " + prompt;
        }
    }
}
