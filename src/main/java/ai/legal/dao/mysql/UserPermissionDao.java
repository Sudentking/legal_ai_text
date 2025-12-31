package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户权限表（user_permission）访问层。
 */
public class UserPermissionDao {

    private static final String GRANT_SQL = "INSERT IGNORE INTO user_permission (user_id, permission_code) VALUES (?, ?)";
    private static final String REVOKE_SQL = "DELETE FROM user_permission WHERE user_id = ? AND permission_code = ?";
    private static final String LIST_SQL = "SELECT permission_code FROM user_permission WHERE user_id = ? ORDER BY permission_code ASC";

    public boolean grant(long userId, String permissionCode) {
        if (userId <= 0 || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(GRANT_SQL)) {
            ps.setLong(1, userId);
            ps.setString(2, permissionCode.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("授予 user_permission 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean revoke(long userId, String permissionCode) {
        if (userId <= 0 || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(REVOKE_SQL)) {
            ps.setLong(1, userId);
            ps.setString(2, permissionCode.trim());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("撤销 user_permission 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public List<String> listPermissionCodes(long userId) {
        List<String> list = new ArrayList<>();
        if (userId <= 0) {
            return list;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(LIST_SQL)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("permission_code");
                    if (code != null && !code.isBlank()) {
                        list.add(code.trim());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 user_permission 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}

