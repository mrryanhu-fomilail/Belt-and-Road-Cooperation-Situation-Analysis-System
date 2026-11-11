package com.bri.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库 schema 快速验证 — 非生产代码，仅用于开发阶段手动验证建表是否正确。
 *
 * <p>用法：直接 run 本类的 main 方法，检查控制台输出。</p>
 */
public final class SchemaVerifier {

    private SchemaVerifier() {}

    /**
     * 手动验证入口。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        String dbPath = "target/test_verify.db";

        // 清理旧文件
        new java.io.File(dbPath).delete();

        System.out.println("=== 数据库 Schema 验证 ===");
        System.out.println("数据库路径: " + dbPath);

        DataSource ds = DataSource.getInstance(dbPath);

        try {
            // 1. 幂等建库
            System.out.print("[1] initIfAbsent() ... ");
            ds.initIfAbsent();
            System.out.println("OK (schemaReady=" + ds.isSchemaReady() + ")");

            // 2. 二次调用（幂等）
            System.out.print("[2] 二次 initIfAbsent() ... ");
            ds.initIfAbsent();
            System.out.println("OK (幂等验证通过)");

            // 3. 检查表
            try (Connection conn = ds.getConnection()) {
                System.out.println("[3] 检查表结构:");
                printTables(conn);

                System.out.println("[4] 检查索引:");
                printIndexes(conn);

                System.out.println("[5] 检查 subregions 种子数据:");
                printSeedData(conn);

                System.out.println("[6] 检查 PRAGMA 配置:");
                printPragmas(conn);
            }

            System.out.println("\n=== 全部验证通过 ===");

        } catch (Exception e) {
            System.err.println("验证失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ds.close();
            new java.io.File(dbPath).delete();
        }
    }

    private static void printTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (rs.next()) {
                String name = rs.getString("name");
                System.out.println("   表: " + name);
                // 打印字段
                try (Statement s2 = conn.createStatement();
                     ResultSet pr = s2.executeQuery("PRAGMA table_info(" + name + ")")) {
                    while (pr.next()) {
                        System.out.printf("     ├─ %-14s %-8s pk=%-5s nullable=%-5s%n",
                                pr.getString("name"),
                                pr.getString("type"),
                                pr.getInt("pk") > 0 ? "YES" : "no",
                                pr.getInt("notnull") == 0 ? "YES" : "no");
                    }
                }
            }
        }
    }

    private static void printIndexes(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index' "
                             + "AND name LIKE 'idx_%' ORDER BY name")) {
            int count = 0;
            while (rs.next()) {
                System.out.println("   " + rs.getString("name"));
                count++;
            }
            System.out.println("   共 " + count + " 条索引");
        }
    }

    private static void printSeedData(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT key, name_cn, name_en FROM subregions ORDER BY key")) {
            while (rs.next()) {
                System.out.printf("   %-4s %-6s %s%n",
                        rs.getString("key"),
                        rs.getString("name_cn"),
                        rs.getString("name_en"));
            }
        }
    }

    private static void printPragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                if (rs.next()) System.out.println("   journal_mode = " + rs.getString(1));
            }
        }
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA synchronous")) {
                if (rs.next()) System.out.println("   synchronous  = " + rs.getString(1));
            }
        }
    }
}
