package ai.legal.service.auth;

import ai.legal.model.PermissionCode;
import ai.legal.model.UserAccount;

import java.util.Collections;
import java.util.List;

/**
 * 登录结果（面向后续 Web/接口返回）。
 */
public class LoginResult {

    private final boolean success;
    private final String message;
    private final String sessionId;
    private final UserAccount user;
    private final List<PermissionCode> permissions;

    private LoginResult(boolean success, String message, String sessionId, UserAccount user, List<PermissionCode> permissions) {
        this.success = success;
        this.message = message;
        this.sessionId = sessionId;
        this.user = user;
        this.permissions = permissions == null ? List.of() : Collections.unmodifiableList(permissions);
    }

    public static LoginResult success(String sessionId, UserAccount user, List<PermissionCode> permissions) {
        return new LoginResult(true, "ok", sessionId, user, permissions);
    }

    public static LoginResult fail(String message) {
        return new LoginResult(false, message == null ? "fail" : message, null, null, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UserAccount getUser() {
        return user;
    }

    public List<PermissionCode> getPermissions() {
        return permissions;
    }
}

