package tk.kahsolt.akasha.db;

import org.apache.log4j.Logger;
import tk.kahsolt.sqlbuilder.SQLBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public abstract class SQLEngine {

    protected static final Logger logger = Logger.getLogger(SQLEngine.class);

    public SQLBuilder sqlBuilder;

    protected String dbUri;
    protected Connection dbConnection;
    public HashMap<String, HashSet<String>> dbSchema;

    // for connection control
    public abstract void connect();
    public void disconnect() {
        try {
            if(!dbConnection.getAutoCommit()) {
                try {
                    dbConnection.commit();  // in case you forget to commit
                } catch (SQLException ignored) { }
            }
            dbConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void fetchMetadata() {
        dbSchema = new HashMap<>();
        try {
            DatabaseMetaData dmd = dbConnection.getMetaData();
            ResultSet rs = dmd.getTables(null, null, null, null);
            while (rs.next()) {
                HashSet<String> columns = new HashSet<>();
                String table = rs.getString("TABLE_NAME");
                dbSchema.put(table, columns);
                ResultSet rss = dmd.getColumns(null, null, table, null);
                try {
                    while (rss.next())
                        columns.add(rss.getString("COLUMN_NAME"));
                } catch (SQLException e) { e.printStackTrace(); }
                rss.close();
            }
            rs.close();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // for DCL
    public void begin() {
        try {
            dbConnection.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void commit() {
        try {
            dbConnection.commit();
            dbConnection.setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     * for DDL and DML
     *   DDL正常返回值==0
     *   DML正常返回值>=0
     *   执行失败返回值==-1
     */
    public int execute(String sqlTemplate, Object... parameters) {
        dumpQuery("\\Execute\\", sqlTemplate, parameters);
        try {
            PreparedStatement ps = dbConnection.prepareStatement(sqlTemplate);
            for (int i = 1; i <= parameters.length; i++) ps.setObject(i, parameters[i-1]);
            int effectedRows = ps.executeUpdate();
            ps.close();
            return effectedRows;
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                if(!dbConnection.getAutoCommit()) dbConnection.rollback();
            } catch (SQLException e1) { e1.printStackTrace(); }
        }
        return -1;
    }

    /*
     * for DQL
     *   acquire    取第一个标量Object          类型映射关系丧失，通常用于SELECT COUNT
     *   fetch      取第一行ArrayList<Object>   类型映射关系丧失，通常用于取三个内定字段的值
     *   query      取结果集ResultSet(记得关闭)  类型映射在Model.modalize()中处理，用于模型化
     */
    public Object acquire(String sqlTemplate, Object... parameters) {
        // dumpQuery("/Acquire/", sqlTemplate, parameters);
        try {
            ResultSet rs = query(sqlTemplate, parameters);
            rs.next();
            Object res = rs.getObject(1);
            rs.close();
            return res;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public ArrayList<Object> fetch(String sqlTemplate, Object... parameters) {
        // dumpQuery("/Fetch/", sqlTemplate, parameters);
        try {
            ResultSet rs = query(sqlTemplate, parameters);
            ArrayList<Object> res = new ArrayList<>();
            rs.next();
            int columnCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) res.add(rs.getObject(i));
            rs.close();
            return res;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    public ResultSet query(String sqlTemplate, Object... parameters) {
        dumpQuery("/Query/", sqlTemplate, parameters);
        try {
            PreparedStatement ps = dbConnection.prepareStatement(sqlTemplate);
            for (int i = 1; i <= parameters.length; i++) ps.setObject(i, parameters[i-1]);
            return ps.executeQuery();
        } catch (SQLException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void dumpQuery(String action, String sqlTemplate, Object... parameters) {
        ArrayList<String> params = new ArrayList<>();
        for (Object parameter : parameters) {
            if (parameter == null) params.add("<NULL>");
            else params.add(parameter.toString());
        }
        String paramStr = String.join(", ", params);
        logger.debug(String.format("%s %s\t%s", action, sqlTemplate, params));
    }

}