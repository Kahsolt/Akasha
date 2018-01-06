package tk.kahsolt.akasha.db;

import tk.kahsolt.sqlbuilder.SQLBuilder;
import tk.kahsolt.sqlbuilder.sql.Dialect;

import java.sql.*;

public class MySQLEngine extends SQLEngine {

    public MySQLEngine() {
        dbUri = "jdbc:mysql://localhost:3306/test?user=root&password=&useUnicode=true&characterEncoding=UTF-8";
        sqlBuilder = new SQLBuilder(Dialect.MYSQL);
    }
    public MySQLEngine(String dbUri) {
        this.dbUri = dbUri;
        sqlBuilder = new SQLBuilder(Dialect.MYSQL);
    }
    public MySQLEngine(String dbHost, String dbPort, String dbSchema, String dbUsername, String dbPassword) {
        dbHost = dbHost != null ? dbHost : "localhost";
        dbPort = dbPort != null ? dbPort : "3306";
        dbSchema = dbSchema != null ? dbSchema : "test";
        dbUsername = dbUsername != null ? dbUsername : "root";
        dbPassword = dbPassword != null ? dbPassword : "";
        this.dbUri = String.format("jdbc:mysql://%s:%s/%s?user=%s&&password=%s&useUnicode=true&characterEncoding=UTF-8",
                dbHost, dbPort, dbSchema, dbUsername, dbPassword);
        sqlBuilder = new SQLBuilder(Dialect.MYSQL);
    }

    @Override
    public void connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            dbConnection = DriverManager.getConnection(this.dbUri);
            dbConnection.setAutoCommit(true);
            fetchMetadata();
        } catch (ClassNotFoundException e) {
            logger.error("Cannot find jdbc driver");
        } catch (SQLException e) {
            e.printStackTrace();
            logger.error("SQL execution error");
        }
    }

}
