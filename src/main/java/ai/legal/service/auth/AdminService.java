package ai.legal.service.auth;

import ai.legal.dao.mysql.UserAccountDao;
import ai.legal.dao.mysql.UserPermissionDao;
import ai.legal.model.PermissionCode;
import ai.legal.model.UserAccount;
import ai.legal.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 超级用户能力：用户管理与权限配置（后续 Web 接口可直接调用）。
 */
public class AdminService {

    private final UserAccountDao userDao;
    private final UserPermissionDao permissionDao;

    public AdminService(UserAccountDao userDao, UserPermissionDao permissionDao) {
        this.userDao = userDao;
        this.permissionDao = permissionDao;
    }

    public boolean grantPermission(UserAccount operator, String targetUsername, PermissionCode permission) {
        requireSuperAdmin(operator);
        if (permission == null) {
            throw new IllegalArgumentException("permission 不能为空");
        }
        UserAccount target = userDao.findByUsername(targetUsername);
        if (target == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }
        return permissionDao.grant(target.getId(), permission.name());
    }

    public boolean revokePermission(UserAccount operator, String targetUsername, PermissionCode permission) {
        requireSuperAdmin(operator);
        if (permission == null) {
            throw new IllegalArgumentException("permission 不能为空");
        }
        UserAccount target = userDao.findByUsername(targetUsername);
        if (target == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }
        return permissionDao.revoke(target.getId(), permission.name());
    }

    public List<PermissionCode> listDirectPermissions(UserAccount operator, String targetUsername) {
        requireSuperAdmin(operator);
        UserAccount target = userDao.findByUsername(targetUsername);
        if (target == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }
        List<String> codes = permissionDao.listPermissionCodes(target.getId());
        List<PermissionCode> perms = new ArrayList<>();
        for (String c : codes) {
            try {
                perms.add(PermissionCode.valueOf(c));
            } catch (IllegalArgumentException e) {
                // ignore unknown
            }
        }
        return perms;
    }

    public boolean promoteToSuperAdmin(UserAccount operator, String targetUsername) {
        requireSuperAdmin(operator);
        UserAccount target = userDao.findByUsername(targetUsername);
        if (target == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }
        return userDao.updateRole(target.getId(), UserRole.SUPER_ADMIN);
    }

    private void requireSuperAdmin(UserAccount operator) {
        if (operator == null || operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new SecurityException("需要超级用户权限");
        }
    }
}

