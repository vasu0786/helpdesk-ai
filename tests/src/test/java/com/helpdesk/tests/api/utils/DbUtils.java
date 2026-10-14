package com.helpdesk.tests.api.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only DB helper: runs a SELECT and returns the rows as maps (column name -> value) */
public class DbUtils {

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                ConfigReader.get("db.url"), ConfigReader.get("db.user"), ConfigReader.get("db.password"));
    }

    /**
     * Runs a SELECT with ? placeholders and returns every row.
     * Example: query("SELECT * FROM tickets WHERE status = ?", "OPEN")
     */
    public static List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]); // JDBC placeholders start at 1, not 0
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        row.put(meta.getColumnLabel(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB query failed: " + sql, e);
        }
    }

    /** Returns the first row, or null when nothing matches */
    public static Map<String, Object> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** For COUNT(*) queries, e.g. count("SELECT COUNT(*) FROM tickets WHERE customer_email = ?", email) */
    public static long count(String sql, Object... params) {
        Map<String, Object> row = queryOne(sql, params);
        return ((Number) row.values().iterator().next()).longValue();
    }
}
