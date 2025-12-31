package ai.legal.dao.mysql;

import ai.legal.config.MySqlConfig;
import ai.legal.model.UserAccount;
import ai.legal.model.UserRole;
import ai.legal.model.UserStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * 用户账号表（user_account）访问层。
 */
public class UserAccountDao {

    private static final String INSERT_SQL = "INSERT INTO user_account " +
            "(username, password_hash, password_salt, role, status) VALUES (?, ?, ?, ?, ?)";
    private static final String FIND_BY_USERNAME_SQL = "SELECT id, username, password_hash, password_salt, role, status, last_login_at, created_at, updated_at " +
            "FROM user_account WHERE username = ? LIMIT 1";
    private static final String FIND_BY_ID_SQL = "SELECT id, username, password_hash, password_salt, role, status, last_login_at, created_at, updated_at " +
            "FROM user_account WHERE id = ? LIMIT 1";
    private static final String UPDATE_LAST_LOGIN_SQL = "UPDATE user_account SET last_login_at = ? WHERE id = ?";
    private static final String UPDATE_ROLE_SQL = "UPDATE user_account SET role = ? WHERE id = ?";
    private static final String UPDATE_STATUS_SQL = "UPDATE user_account SET status = ? WHERE id = ?";
    private static final String UPDATE_PASSWORD_SQL = "UPDATE user_account SET password_hash = ?, password_salt = ? WHERE id = ?";
    private static final String EXISTS_SUPER_ADMIN_SQL = "SELECT 1 FROM user_account WHERE role = 'SUPER_ADMIN' LIMIT 1";

    public long insert(UserAccount user) {
        if (user == null) {
            return -1L;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getPasswordSalt());
            ps.setString(4, user.getRole() == null ? UserRole.USER.name() : user.getRole().name());
            ps.setString(5, user.getStatus() == null ? UserStatus.ACTIVE.name() : user.getStatus().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    user.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("插入 user_account 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return -1L;
    }

    public UserAccount findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 user_account 失败(username): " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public UserAccount findById(long id) {
        if (id <= 0) {
            return null;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("查询 user_account 失败(id): " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public boolean updateLastLoginAt(long userId, Timestamp ts) {
        if (userId <= 0) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_LAST_LOGIN_SQL)) {
            ps.setTimestamp(1, ts == null ? new Timestamp(System.currentTimeMillis()) : ts);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("更新 user_account.last_login_at 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateRole(long userId, UserRole role) {
        if (userId <= 0 || role == null) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_ROLE_SQL)) {
            ps.setString(1, role.name());
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("更新 user_account.role 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateStatus(long userId, UserStatus status) {
        if (userId <= 0 || status == null) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS_SQL)) {
            ps.setString(1, status.name());
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("更新 user_account.status 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updatePassword(long userId, String passwordHash, String passwordSalt) {
        if (userId <= 0 || passwordHash == null || passwordSalt == null) {
            return false;
        }
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
            ps.setString(1, passwordHash);
            ps.setString(2, passwordSalt);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("更新 user_account.password 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean existsSuperAdmin() {
        try (Connection connection = MySqlConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(EXISTS_SUPER_ADMIN_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            System.err.println("查询 user_account 是否存在 SUPER_ADMIN 失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private UserAccount mapRow(ResultSet rs) throws SQLException {
        UserAccount user = new UserAccount();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setRole(parseRole(rs.getString("role")));
        user.setStatus(parseStatus(rs.getString("status")));
        user.setLastLoginAt(rs.getTimestamp("last_login_at"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setUpdatedAt(rs.getTimestamp("updated_at"));
        return user;
    }

    private UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(role.trim());
        } catch (IllegalArgumentException e) {
            return UserRole.USER;
        }
    }

    private UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserStatus.ACTIVE;
        }
        try {
            return UserStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            return UserStatus.ACTIVE;
        }
    }
}

