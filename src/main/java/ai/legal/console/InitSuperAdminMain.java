package ai.legal.console;

import ai.legal.dao.mysql.UserAccountDao;
import ai.legal.dao.mysql.UserPermissionDao;
import ai.legal.dao.mysql.UserSessionDao;
import ai.legal.model.UserAccount;
import ai.legal.service.auth.AuthService;

/**
 * 一次性初始化/重置超级用户（用于本地开发或运维脚本）。
 *
 * <p>默认账号：root_user / root_password（也可通过 args 覆盖）。</p>
 */
public class InitSuperAdminMain {

    public static void main(String[] args) {
        String username = (args != null && args.length >= 1 && args[0] != null && !args[0].isBlank())
                ? args[0].trim()
                : "root_user";
        String password = (args != null && args.length >= 2 && args[1] != null && !args[1].isBlank())
                ? args[1]
                : "root_password";

        try {
            AuthService authService = new AuthService(new UserAccountDao(), new UserSessionDao(), new UserPermissionDao());
            UserAccount admin = authService.ensureSuperAdmin(username, password);
            System.out.println("超级用户已就绪：username=" + admin.getUsername()
                    + " id=" + admin.getId()
                    + " role=" + admin.getRole());
            System.out.println("注意：若该用户已存在，本命令会重置密码。");
        } catch (Exception e) {
            System.err.println("创建/重置超级用户失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            System.err.println("请确认：1) mysql.url 指向的库可连接；2) 已执行 sql/mysql_user_auth_schema.sql。");
            e.printStackTrace();
        }
    }
}

