package ai.legal.console;

import ai.legal.dao.LegalEmbeddingDao;
import ai.legal.dao.mysql.UserAccountDao;
import ai.legal.dao.mysql.LawTextDao;
import ai.legal.dao.mysql.UserPermissionDao;
import ai.legal.dao.mysql.UserSessionDao;
import ai.legal.model.PermissionCode;
import ai.legal.model.UserAccount;
import ai.legal.rag.agent.LegalAgentService;
import ai.legal.rag.intent.LegalIntentClassifier;
import ai.legal.rag.service.DeepSeekLlmClient;
import ai.legal.rag.service.LegalRagQaService;
import ai.legal.rag.service.LlmClient;
import ai.legal.rag.service.StructuredLawQueryService;
import ai.legal.service.VectorSearchService;
import ai.legal.service.auth.AdminService;
import ai.legal.service.auth.AuthService;
import ai.legal.service.auth.LoginResult;

import java.util.Scanner;
import java.util.UUID;

/**
 * 命令行入口：多轮对话，输入→调用→输出。
 */
public class LegalQaCli {

    public static void main(String[] args) {
        System.out.println("欢迎使用法律智能问答系统（支持注册/登录/超级用户权限）。");
        System.out.println("输入法律问题开始咨询，输入 exit 或 quit 退出。");
        System.out.println("命令：");
        System.out.println("  :register <username> <password>");
        System.out.println("  :login <username> <password>");
        System.out.println("  :logout");
        System.out.println("  :whoami");
        System.out.println("  :init-admin <username> <password>   （创建/重置超级用户）");
        System.out.println("  :promote <username>                （提升为超级用户，超级用户）");
        System.out.println("  :grant <username> <PERMISSION_CODE> （超级用户）");
        System.out.println("  :revoke <username> <PERMISSION_CODE>（超级用户）");
        System.out.println("  :list-user-perms <username>        （查看用户直授权限，超级用户）");
        System.out.println("  :perms                              （列出权限码）");

        LlmClient llmClient = createLlmClient();
        VectorSearchService vectorSearchService = new VectorSearchService(new LegalEmbeddingDao());
        LegalRagQaService ragQaService = new LegalRagQaService(vectorSearchService, llmClient);
        StructuredLawQueryService structuredLawQueryService = new StructuredLawQueryService(new LawTextDao());
        LegalAgentService agentService = new LegalAgentService(new LegalIntentClassifier(), ragQaService, structuredLawQueryService, llmClient);

        UserAccountDao userAccountDao = new UserAccountDao();
        UserPermissionDao userPermissionDao = new UserPermissionDao();
        UserSessionDao userSessionDao = new UserSessionDao();
        AuthService authService = new AuthService(userAccountDao, userSessionDao, userPermissionDao);
        AdminService adminService = new AdminService(userAccountDao, userPermissionDao);

        Long currentUserId = 0L;
        String currentSessionId = "cli_" + UUID.randomUUID();
        UserAccount currentUser = null;

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String who = currentUser == null ? "guest" : (currentUser.getUsername() + "#" + currentUser.getId());
                System.out.print("[" + who + "]> ");
                String question = scanner.nextLine();
                if (question == null) {
                    continue;
                }
                String trimmed = question.trim();
                if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
                    System.out.println("已退出。");
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith(":")) {
                    handleCommand(trimmed, authService);
                    // 命令可能改变登录态，从 authService 重新取 session/user
                    if (trimmed.startsWith(":login ")) {
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 3) {
                            LoginResult lr = authService.login(parts[1], parts[2]);
                            if (lr.isSuccess()) {
                                currentSessionId = lr.getSessionId();
                                currentUser = lr.getUser();
                                currentUserId = currentUser.getId();
                                System.out.println("登录成功，sessionId=" + currentSessionId
                                        + " role=" + currentUser.getRole()
                                        + " perms=" + lr.getPermissions());
                            } else {
                                System.out.println("登录失败：" + lr.getMessage());
                            }
                        }
                    }
                    if (trimmed.equals(":logout")) {
                        authService.logout(currentSessionId);
                        currentSessionId = "cli_" + UUID.randomUUID();
                        currentUserId = 0L;
                        currentUser = null;
                        System.out.println("已退出登录。");
                    }
                    if (trimmed.startsWith(":init-admin ")) {
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length >= 3) {
                            UserAccount admin = authService.ensureSuperAdmin(parts[1], parts[2]);
                            System.out.println("超级用户已就绪：username=" + admin.getUsername() + " id=" + admin.getId());
                        }
                    }
                    if (trimmed.equals(":whoami")) {
                        if (currentUser == null) {
                            System.out.println("未登录（guest），sessionId=" + currentSessionId);
                        } else {
                            System.out.println("当前用户：id=" + currentUser.getId()
                                    + " username=" + currentUser.getUsername()
                                    + " role=" + currentUser.getRole()
                                    + " sessionId=" + currentSessionId);
                        }
                    }
                    if (trimmed.startsWith(":grant ") || trimmed.startsWith(":revoke ")) {
                        if (currentUser == null) {
                            System.out.println("请先登录超级用户。");
                        } else {
                            String[] parts = trimmed.split("\\s+");
                            if (parts.length >= 3) {
                                String target = parts[1];
                                PermissionCode perm;
                                try {
                                    perm = PermissionCode.valueOf(parts[2]);
                                } catch (IllegalArgumentException e) {
                                    System.out.println("未知权限码：" + parts[2]);
                                    continue;
                                }
                                try {
                                    boolean ok;
                                    if (trimmed.startsWith(":grant ")) {
                                        ok = adminService.grantPermission(currentUser, target, perm);
                                    } else {
                                        ok = adminService.revokePermission(currentUser, target, perm);
                                    }
                                    System.out.println(ok ? "操作成功" : "操作失败");
                                } catch (Exception e) {
                                    System.out.println("操作失败：" + e.getMessage());
                                }
                            }
                        }
                    }
                    if (trimmed.startsWith(":promote ")) {
                        if (currentUser == null) {
                            System.out.println("请先登录超级用户。");
                        } else {
                            String[] parts = trimmed.split("\\s+");
                            if (parts.length >= 2) {
                                try {
                                    boolean ok = adminService.promoteToSuperAdmin(currentUser, parts[1]);
                                    System.out.println(ok ? "操作成功" : "操作失败");
                                } catch (Exception e) {
                                    System.out.println("操作失败：" + e.getMessage());
                                }
                            } else {
                                System.out.println("用法：:promote <username>");
                            }
                        }
                    }
                    if (trimmed.startsWith(":list-user-perms ")) {
                        if (currentUser == null) {
                            System.out.println("请先登录超级用户。");
                        } else {
                            String[] parts = trimmed.split("\\s+");
                            if (parts.length >= 2) {
                                try {
                                    System.out.println("用户直授权限：" + adminService.listDirectPermissions(currentUser, parts[1]));
                                } catch (Exception e) {
                                    System.out.println("查询失败：" + e.getMessage());
                                }
                            } else {
                                System.out.println("用法：:list-user-perms <username>");
                            }
                        }
                    }
                    if (trimmed.equals(":perms")) {
                        System.out.println("可用权限码：" + PermissionCode.all());
                    }
                    continue;
                }

                String response = agentService.answer(currentUserId, currentSessionId, trimmed, "");
                System.out.println(response);
            }
        }
    }

    private static void handleCommand(String cmd, AuthService authService) {
        // 仅做最小校验与提示；登录态由 main 内部维护
        if (cmd.startsWith(":register ")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length < 3) {
                System.out.println("用法：:register <username> <password>");
                return;
            }
            try {
                UserAccount user = authService.register(parts[1], parts[2]);
                System.out.println("注册成功：id=" + user.getId() + " username=" + user.getUsername());
            } catch (Exception e) {
                System.out.println("注册失败：" + e.getMessage());
            }
            return;
        }
        if (cmd.startsWith(":login ")) {
            System.out.println("正在登录...");
            return;
        }
        if (cmd.equals(":logout") || cmd.equals(":whoami") || cmd.equals(":perms")) {
            return;
        }
        if (cmd.startsWith(":init-admin ")) {
            System.out.println("正在初始化超级用户...");
            return;
        }
        if (cmd.startsWith(":grant ") || cmd.startsWith(":revoke ")) {
            return;
        }
        if (cmd.startsWith(":promote ") || cmd.startsWith(":list-user-perms ")) {
            return;
        }
        System.out.println("未知命令：" + cmd);
        System.out.println("可用命令：:register / :login / :logout / :whoami / :init-admin / :promote / :grant / :revoke / :list-user-perms / :perms");
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
