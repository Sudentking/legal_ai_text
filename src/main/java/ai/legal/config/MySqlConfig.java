package ai.legal.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * MySQL 连接配置与获取。
 */
public class MySqlConfig {

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        Properties properties = new Properties();
        try (InputStream input = MySqlConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IllegalStateException("未找到 application.properties 配置文件");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("读取数据库配置失败", e);
        }

        URL = properties.getProperty("mysql.url");
        USER = properties.getProperty("mysql.user");
        PASSWORD = properties.getProperty("mysql.password");

        if (URL == null || USER == null || PASSWORD == null) {
            throw new IllegalStateException("MySQL 连接信息缺失，请检查 application.properties");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 MySQL JDBC 驱动", e);
        }
    }

    private MySqlConfig() {
    }

    /**
     * 获取新的 MySQL 连接。
     *
     * @return Connection
     * @throws SQLException 获取连接异常
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
