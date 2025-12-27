package ai.legal.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 简单的数据库配置与连接获取。
 */
public class DatabaseConfig {

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        // 读取类路径下 application.properties 的数据库配置
        Properties properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IllegalStateException("未找到 application.properties 配置文件");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("读取数据库配置失败", e);
        }

        URL = properties.getProperty("db.url");
        USER = properties.getProperty("db.user");
        PASSWORD = properties.getProperty("db.password");

        // 校验配置完整性
        if (URL == null || USER == null || PASSWORD == null) {
            throw new IllegalStateException("数据库连接信息缺失，请检查 application.properties");
        }

        // 提前加载 PostgreSQL JDBC 驱动
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 PostgreSQL JDBC 驱动", e);
        }
    }

    private DatabaseConfig() {
    }

    // 获取新的数据库连接，调用方需负责关闭
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
