package ai.legal.web;

import ai.legal.dao.mysql.AgentTaskHistoryDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.dao.mysql.QaLogDao;
import ai.legal.importer.LawDocumentImportService;
import ai.legal.rag.agent.LegalAgentService;
import ai.legal.service.kb.KnowledgeBaseMaintenanceService;
import ai.legal.service.kb.KnowledgeBaseQualityService;
import ai.legal.service.auth.AdminService;
import ai.legal.service.auth.AuthService;

/**
 * Web 层依赖聚合（便于 Handler 复用与后续扩展）。
 */
public class WebAppContext {

    private final AuthService authService;
    private final AdminService adminService;
    private final LegalAgentService agentService;
    private final LawDocumentImportService importService;
    private final LawTextDao lawTextDao;
    private final KnowledgeBaseMaintenanceService kbMaintenanceService;
    private final KnowledgeBaseQualityService kbQualityService;
    private final QaLogDao qaLogDao;
    private final AgentTaskHistoryDao taskHistoryDao;

    public WebAppContext(AuthService authService,
                         AdminService adminService,
                         LegalAgentService agentService,
                         LawDocumentImportService importService,
                         LawTextDao lawTextDao,
                         KnowledgeBaseMaintenanceService kbMaintenanceService,
                         KnowledgeBaseQualityService kbQualityService,
                         QaLogDao qaLogDao,
                         AgentTaskHistoryDao taskHistoryDao) {
        this.authService = authService;
        this.adminService = adminService;
        this.agentService = agentService;
        this.importService = importService;
        this.lawTextDao = lawTextDao;
        this.kbMaintenanceService = kbMaintenanceService;
        this.kbQualityService = kbQualityService;
        this.qaLogDao = qaLogDao;
        this.taskHistoryDao = taskHistoryDao;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AdminService getAdminService() {
        return adminService;
    }

    public LegalAgentService getAgentService() {
        return agentService;
    }

    public LawDocumentImportService getImportService() {
        return importService;
    }

    public LawTextDao getLawTextDao() {
        return lawTextDao;
    }

    public KnowledgeBaseMaintenanceService getKbMaintenanceService() {
        return kbMaintenanceService;
    }

    public KnowledgeBaseQualityService getKbQualityService() {
        return kbQualityService;
    }

    public QaLogDao getQaLogDao() {
        return qaLogDao;
    }

    public AgentTaskHistoryDao getTaskHistoryDao() {
        return taskHistoryDao;
    }
}
