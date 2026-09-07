package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SQLiteHelper {

    private static final String DEFAULT_DB_NAME = "app";
    private static final String DB_DIR = "data";

    // 存储多个数据库连接，key为数据库名称
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private String getDbPath(String dbName) {
        if (dbName == null || dbName.trim().isEmpty()) {
            dbName = DEFAULT_DB_NAME;
        }
        return DB_DIR + "/" + dbName + ".db";
    }

    private synchronized Connection getConnection(String dbName) throws SQLException {
        if (dbName == null || dbName.trim().isEmpty()) {
            dbName = DEFAULT_DB_NAME;
        }
        Connection conn = connections.get(dbName);
        if (conn == null || conn.isClosed()) {
            String dbPath = getDbPath(dbName);
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connections.put(dbName, conn);
        }
        return conn;
    }

    public void close(String dbName) {
        if (dbName == null || dbName.trim().isEmpty()) {
            dbName = DEFAULT_DB_NAME;
        }
        try {
            Connection conn = connections.remove(dbName);
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("关闭连接失败: " + e.getMessage(), e);
        }
    }

    public void closeAll() {
        for (String dbName : connections.keySet()) {
            close(dbName);
        }
    }

    // execute 方法
    public boolean execute(String sql) {
        return execute(null, sql);
    }

    public boolean execute(String dbName, String sql) {
        try (Statement stmt = getConnection(dbName).createStatement()) {
            return stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("执行SQL失败: " + e.getMessage(), e);
        }
    }

    // update 方法
    public int update(String sql) {
        return update(null, sql);
    }

    public int update(String dbName, String sql) {
        try (Statement stmt = getConnection(dbName).createStatement()) {
            return stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("执行更新失败: " + e.getMessage(), e);
        }
    }

    // query 方法
    public List<Map<String, Object>> query(String sql) {
        return query(null, sql);
    }

    public List<Map<String, Object>> query(String dbName, String sql) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Statement stmt = getConnection(dbName).createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        return results;
    }

    // queryOne 方法
    public Map<String, Object> queryOne(String sql) {
        return queryOne(null, sql);
    }

    public Map<String, Object> queryOne(String dbName, String sql) {
        List<Map<String, Object>> results = query(dbName, sql);
        return results.isEmpty() ? null : results.get(0);
    }

    // insert 方法
    public int insert(String table, Map<String, Object> data) {
        return insert(null, table, data);
    }

    public int insert(String dbName, String table, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("插入数据不能为空");
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table).append(" (");
        StringBuilder values = new StringBuilder(") VALUES (");

        List<Object> params = new ArrayList<>();
        List<String> columns = new ArrayList<>(data.keySet());
        for (int i = 0; i < columns.size(); i++) {
            sql.append(columns.get(i));
            values.append("?");
            params.add(data.get(columns.get(i)));
            if (i < columns.size() - 1) {
                sql.append(", ");
                values.append(", ");
            }
        }
        sql.append(values).append(")");

        try (PreparedStatement pstmt = getConnection(dbName).prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入失败: " + e.getMessage(), e);
        }
    }

    // delete 方法
    public int delete(String table, String where) {
        return delete(null, table, where);
    }

    public int delete(String dbName, String table, String where) {
        String sql = "DELETE FROM " + table;
        if (where != null && !where.trim().isEmpty()) {
            sql += " WHERE " + where;
        }
        return update(dbName, sql);
    }

    // updateRecords 方法
    public int updateRecords(String table, Map<String, Object> data, String where) {
        return updateRecords(null, table, data, where);
    }

    public int updateRecords(String dbName, String table, Map<String, Object> data, String where) {
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("更新数据不能为空");
        }
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(table).append(" SET ");

        List<Object> params = new ArrayList<>();
        List<String> columns = new ArrayList<>(data.keySet());
        for (int i = 0; i < columns.size(); i++) {
            sql.append(columns.get(i)).append(" = ?");
            params.add(data.get(columns.get(i)));
            if (i < columns.size() - 1) {
                sql.append(", ");
            }
        }
        if (where != null && !where.trim().isEmpty()) {
            sql.append(" WHERE ").append(where);
        }

        try (PreparedStatement pstmt = getConnection(dbName).prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("更新失败: " + e.getMessage(), e);
        }
    }

    // createTable 方法
    public void createTable(String tableName, String columnsDef) {
        createTable(null, tableName, columnsDef);
    }

    public void createTable(String dbName, String tableName, String columnsDef) {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + columnsDef + ")";
        execute(dbName, sql);
    }

    // queryWithParams 方法
    public List<Map<String, Object>> queryWithParams(String sql, Object[] params) {
        return queryWithParams(null, sql, params);
    }

    public List<Map<String, Object>> queryWithParams(String dbName, String sql, Object[] params) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement pstmt = getConnection(dbName).prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
        return results;
    }
}
