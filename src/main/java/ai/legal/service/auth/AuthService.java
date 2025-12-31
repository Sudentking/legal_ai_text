package ai.legal.service.auth;

import ai.legal.dao.mysql.UserAccountDao;
import ai.legal.dao.mysql.UserPermissionDao;
import ai.legal.dao.mysql.UserSessionDao;
import ai.legal.model.PermissionCode;
import ai.legal.model.UserAccount;
import ai.legal.model.UserRole;
import ai.legal.model.UserSession;
import ai.legal.model.UserSessionStatus;
import ai.legal.model.UserStatus;
import ai.legal.security.PasswordHasher;
import ai.legal.security.PasswordHasher.PasswordHash;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 注册/登录/会话鉴权服务（可直接被后续 Web Controller 调用）。
 */
public class AuthService {

    private final UserAccountDao userDao;
    private final UserSessionDao sessionDao;
    private final UserPermissionDao permissionDao;
    private final PasswordHasher passwordHasher;

    public AuthService(UserAccountDao userDao, UserSessionDao sessionDao, UserPermissionDao permissionDao) {
        this(userDao, sessionDao, permissionDao, new PasswordHasher());
    }

    public AuthService(UserAccountDao userDao, UserSessionDao sessionDao, UserPermissionDao permissionDao, PasswordHasher passwordHasher) {
        this.userDao = userDao;
        this.sessionDao = sessionDao;
        this.permissionDao = permissionDao;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 普通用户注册（默认 USER/ACTIVE）。
     */
    public UserAccount register(String username, String password) {
        String u = normalizeUsername(username);
        validatePassword(password);
        if (userDao.findByUsername(u) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        PasswordHash ph = passwordHasher.hash(password);
        UserAccount user = new UserAccount(u, ph.getHashBase64(), ph.getSaltBase64(), UserRole.USER, UserStatus.ACTIVE);
        long id = userDao.insert(user);
        if (id <= 0) {
            throw new IllegalStateException("注册失败：写入数据库失败");
        }
        // 默认赋予最基础权限，避免后续接入权限校验后“注册即不可用”
        permissionDao.grant(id, PermissionCode.QA_ASK.name());
        return sanitize(user);
    }

    /**
     * 登录：校验密码 → 创建 session → 返回 sessionId。
     */
    public LoginResult login(String username, String password) {
        String u = normalizeUsername(username);
        validatePassword(password);
        UserAccount user = userDao.findByUsername(u);
        if (user == null) {
            return LoginResult.fail("用户名或密码错误");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            return LoginResult.fail("账号已被禁用");
        }
        boolean ok = passwordHasher.verify(password, user.getPasswordHash(), user.getPasswordSalt());
        if (!ok) {
            return LoginResult.fail("用户名或密码错误");
        }

        String sessionId = "sess_" + UUID.randomUUID();
        Timestamp expiresAt = null; // 预留：后续可改为有过期时间
        UserSession session = new UserSession(sessionId, user.getId(), UserSessionStatus.ACTIVE, expiresAt);
        long sid = sessionDao.insert(session);
        if (sid <= 0) {
            return LoginResult.fail("登录失败：创建会话失败");
        }
        userDao.updateLastLoginAt(user.getId(), new Timestamp(System.currentTimeMillis()));

        List<PermissionCode> perms = resolveEffectivePermissions(user);
        return LoginResult.success(sessionId, sanitize(user), perms);
    }

    public boolean logout(String sessionId) {
        return sessionDao.revoke(sessionId);
    }

    /**
     * 按 sessionId 获取已登录用户（用于后续 Web 鉴权）。
     */
    public UserAccount authenticate(String sessionId) {
        UserSession session = sessionDao.findActiveBySessionId(sessionId);
        if (session == null) {
            return null;
        }
        UserAccount user = userDao.findById(session.getUserId());
        if (user == null || user.getStatus() == UserStatus.DISABLED) {
            return null;
        }
        return sanitize(user);
    }

    /**
     * 创建或提升超级用户（用于初始化/运维）。
     * - 不存在则创建 SUPER_ADMIN
     * - 存在则提升为 SUPER_ADMIN，并重置密码
     */
    public UserAccount ensureSuperAdmin(String username, String password) {
        String u = normalizeUsername(username);
        validatePassword(password);
        UserAccount existing = userDao.findByUsername(u);
        PasswordHash ph = passwordHasher.hash(password);
        if (existing == null) {
            UserAccount admin = new UserAccount(u, ph.getHashBase64(), ph.getSaltBase64(), UserRole.SUPER_ADMIN, UserStatus.ACTIVE);
            long id = userDao.insert(admin);
            if (id <= 0) {
                throw new IllegalStateException("创建超级用户失败：写入数据库失败");
            }
            return sanitize(admin);
        }
        userDao.updateRole(existing.getId(), UserRole.SUPER_ADMIN);
        userDao.updateStatus(existing.getId(), UserStatus.ACTIVE);
        userDao.updatePassword(existing.getId(), ph.getHashBase64(), ph.getSaltBase64());
        UserAccount refreshed = userDao.findByUsername(u);
        return refreshed == null ? sanitize(existing) : sanitize(refreshed);
    }

    public List<PermissionCode> resolveEffectivePermissions(UserAccount user) {
        if (user == null) {
            return List.of();
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return PermissionCode.all();
        }
        List<String> codes = permissionDao.listPermissionCodes(user.getId());
        List<PermissionCode> perms = new ArrayList<>();
        for (String c : codes) {
            PermissionCode p = parsePermission(c);
            if (p != null) {
                perms.add(p);
            }
        }
        return perms;
    }

    private PermissionCode parsePermission(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return PermissionCode.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String u = username.trim();
        if (u.length() < 3 || u.length() > 64) {
            throw new IllegalArgumentException("用户名长度需为 3~64");
        }
        if (u.contains(" ") || u.contains("\t") || u.contains("\n")) {
            throw new IllegalArgumentException("用户名不能包含空白");
        }
        return u;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
    }

    private UserAccount sanitize(UserAccount user) {
        if (user == null) {
            return null;
        }
        UserAccount copy = new UserAccount();
        copy.setId(user.getId());
        copy.setUsername(user.getUsername());
        copy.setRole(user.getRole());
        copy.setStatus(user.getStatus());
        copy.setLastLoginAt(user.getLastLoginAt());
        copy.setCreatedAt(user.getCreatedAt());
        copy.setUpdatedAt(user.getUpdatedAt());
        return copy;
    }
}
