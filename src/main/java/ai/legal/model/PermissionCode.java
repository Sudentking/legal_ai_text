package ai.legal.model;

import java.util.Arrays;
import java.util.List;

/**
 * 权限码（用于后续 Web 接口的细粒度鉴权）。
 */
public enum PermissionCode {
    QA_ASK,
    LAW_IMPORT,
    LOG_VIEW,
    USER_MANAGE,
    PERMISSION_MANAGE;

    public static List<PermissionCode> all() {
        return Arrays.asList(values());
    }
}

