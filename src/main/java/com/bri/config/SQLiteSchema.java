package com.bri.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 数据库模式管理器 — DDL 建表与索引创建。
 *
 * <p>负责创建 events、countries、subregions 三张表及性能索引。
 * 所有 DDL 语句均使用 {@code IF NOT EXISTS}，天然幂等，
 * 多次调用不会破坏已有数据。</p>
 *
 * <h3>表结构总览</h3>
 * <table>
 *   <tr><th>表名</th><th>主键</th><th>用途</th></tr>
 *   <tr><td>events</td><td>event_id</td><td>GDELT 事件主表</td></tr>
 *   <tr><td>countries</td><td>cameo_code</td><td>沿线国家元数据</td></tr>
 *   <tr><td>subregions</td><td>key</td><td>子区域字典</td></tr>
 * </table>
 *
 * <h3>索引策略（保证查询 &lt; 3 秒）</h3>
 * <ul>
 *   <li>{@code idx_events_date} — 日期范围查询加速</li>
 *   <li>{@code idx_events_actor1} — 施动国筛选加速</li>
 *   <li>{@code idx_events_is_coop} — 合作/冲突分类加速</li>
 *   <li>{@code idx_countries_subregion} — 子区域筛选加速</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see DataSource
 */
public final class SQLiteSchema {

    private SQLiteSchema() {
        // 工具类，禁止实例化
    }

    // ==================== 建表 DDL ====================

    /** events 事件主表 */
    static final String CREATE_EVENTS =
            "CREATE TABLE IF NOT EXISTS events ("
                    + "event_id     INTEGER PRIMARY KEY,"
                    + "event_date   TEXT    NOT NULL,"
                    + "actor1_code  TEXT,"
                    + "actor2_code  TEXT,"
                    + "event_root   TEXT,"
                    + "event_code   TEXT,"
                    + "is_coop      INTEGER NOT NULL DEFAULT -1,"
                    + "goldstein    REAL,"
                    + "avg_tone     REAL,"
                    + "num_mentions INTEGER DEFAULT 0,"
                    + "lat          REAL,"
                    + "lon          REAL,"
                    + "source_url   TEXT"
                    + ")";

    /** countries 国家元数据表 */
    static final String CREATE_COUNTRIES =
            "CREATE TABLE IF NOT EXISTS countries ("
                    + "cameo_code TEXT PRIMARY KEY,"
                    + "name_cn    TEXT NOT NULL,"
                    + "name_en    TEXT NOT NULL,"
                    + "subregion  TEXT,"
                    + "is_bri     INTEGER NOT NULL DEFAULT 0"
                    + ")";

    /** subregions 子区域字典表 */
    static final String CREATE_SUBREGIONS =
            "CREATE TABLE IF NOT EXISTS subregions ("
                    + "key     TEXT PRIMARY KEY,"
                    + "name_cn TEXT NOT NULL,"
                    + "name_en TEXT NOT NULL"
                    + ")";

    /** downloaded_files 已下载文件追踪表 */
    static final String CREATE_DOWNLOADED_FILES =
            "CREATE TABLE IF NOT EXISTS downloaded_files ("
                    + "file_name    TEXT PRIMARY KEY,"
                    + "file_date    TEXT    NOT NULL,"
                    + "download_time TEXT   NOT NULL,"
                    + "file_size    INTEGER,"
                    + "record_count INTEGER DEFAULT 0,"
                    + "status       TEXT    NOT NULL DEFAULT 'completed'"
                    + ")";

    // ==================== 索引 DDL ====================

    /** 事件日期索引 — 加速按日期范围查询 (F-06/F-07/F-08) */
    static final String INDEX_EVENTS_DATE =
            "CREATE INDEX IF NOT EXISTS idx_events_date "
                    + "ON events(event_date)";

    /** 施动国索引 — 加速按国家筛选 (F-06/F-07 双边关系) */
    static final String INDEX_EVENTS_ACTOR1 =
            "CREATE INDEX IF NOT EXISTS idx_events_actor1 "
                    + "ON events(actor1_code)";

    /** 合作/冲突标记索引 — 加速分类统计 (F-09/F-10/F-18) */
    static final String INDEX_EVENTS_IS_COOP =
            "CREATE INDEX IF NOT EXISTS idx_events_is_coop "
                    + "ON events(is_coop)";

    /** 子区域索引 — 加速按子区域筛选沿线国家 (F-05) */
    static final String INDEX_COUNTRIES_SUBREGION =
            "CREATE INDEX IF NOT EXISTS idx_countries_subregion "
                    + "ON countries(subregion)";

    /** 已下载文件日期索引 — 加速日期范围去重查询 */
    static final String INDEX_DOWNLOADED_FILES_DATE =
            "CREATE INDEX IF NOT EXISTS idx_downloaded_files_date "
                    + "ON downloaded_files(file_date)";

    // ==================== 种子数据 ====================

    /** 迁移：为已有 events 表添加 source_url 列（幂等，使用 ALTER TABLE ADD COLUMN） */
    static final String MIGRATE_EVENTS_SOURCE_URL =
            "ALTER TABLE events ADD COLUMN source_url TEXT";

    /** 子区域字典初始数据 */
    static final String SEED_SUBREGIONS =
            "INSERT OR IGNORE INTO subregions(key, name_cn, name_en) VALUES "
                    + "('EA',  '东亚', 'East Asia'), "
                    + "('SEA', '东南亚', 'Southeast Asia'), "
                    + "('CA',  '中亚', 'Central Asia'), "
                    + "('SA',  '南亚', 'South Asia'), "
                    + "('ME',  '中东', 'Middle East'), "
                    + "('EU',  '欧洲', 'Europe'), "
                    + "('AF',  '非洲', 'Africa'), "
                    + "('NAM', '北美洲', 'North America'), "
                    + "('SAM', '南美洲', 'South America'), "
                    + "('OCE', '大洋洲', 'Oceania')";

    // ==================== 公共方法 ====================

    /**
     * 执行全部建表与索引操作（幂等）。
     *
     * <p>包含：三张表创建 → 四条索引创建 → 子区域字典种子数据插入。
     * 所有 SQL 均使用 {@code IF NOT EXISTS} / {@code OR IGNORE}，
     * 可安全重复调用。</p>
     *
     * @param conn 数据库连接（需由调用方关闭）
     * @throws SQLException 建表失败时抛出
     */
    public static void createAll(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 1. 建表
            stmt.execute(CREATE_EVENTS);
            stmt.execute(CREATE_COUNTRIES);
            stmt.execute(CREATE_SUBREGIONS);
            stmt.execute(CREATE_DOWNLOADED_FILES);

            // 2. 建索引
            stmt.execute(INDEX_EVENTS_DATE);
            stmt.execute(INDEX_EVENTS_ACTOR1);
            stmt.execute(INDEX_EVENTS_IS_COOP);
            stmt.execute(INDEX_COUNTRIES_SUBREGION);
            stmt.execute(INDEX_DOWNLOADED_FILES_DATE);

            // 3. 种子数据
            stmt.execute(SEED_SUBREGIONS);

            // 4. 迁移：为已有 events 表添加 source_url（忽略列已存在的错误）
            try {
                stmt.execute(MIGRATE_EVENTS_SOURCE_URL);
            } catch (SQLException ignored) {
                // 列已存在则忽略
            }
        }
    }

    /**
     * 检查数据库 schema 是否已就绪（检查所有必要表是否存在）。
     *
     * @param conn 数据库连接
     * @return {@code true} 如果所有必需表都已存在
     * @throws SQLException 查询失败时抛出
     */
    public static boolean isSchemaReady(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master "
                             + "WHERE type='table' AND name IN ('events','countries','subregions','downloaded_files')")) {
            return rs.next() && rs.getInt(1) == 4;
        }
    }
}
