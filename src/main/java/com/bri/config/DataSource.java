package com.bri.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理工具 — 基于 HikariCP 连接池的 SQLite 数据源单例。
 *
 * <p>核心特性：</p>
 * <ul>
 *   <li><b>单例模式</b>：全局唯一 HikariCP 连接池，线程安全</li>
 *   <li><b>WAL 模式</b>：{@code PRAGMA journal_mode=WAL} 允许多读一写，大幅提升并发</li>
 *   <li><b>同步优化</b>：{@code PRAGMA synchronous=NORMAL} 在安全前提下加速批量写入</li>
 *   <li><b>幂等建库</b>：{@link #initIfAbsent()} 首次调用自动建表，后续调用无副作用</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DataSource ds = DataSource.getInstance("bri.db");
 * ds.initIfAbsent();                     // 首次：建表 + 种子数据
 * try (Connection conn = ds.getConnection()) {
 *     // 执行查询或写入
 * }
 * // 应用退出时
 * DataSource.getInstance().close();
 * }</pre>
 *
 * <h3>连接池参数</h3>
 * <ul>
 *   <li>最小空闲连接：1</li>
 *   <li>最大连接数：2（SQLite 写串行，2 已覆盖读写并发场景）</li>
 *   <li>连接超时：5 秒</li>
 *   <li>泄漏检测：30 秒</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see SQLiteSchema
 */
public final class DataSource implements AutoCloseable {

    /** 单例实例（volatile 保证可见性） */
    private static volatile DataSource instance;

    private static final Logger LOG = LoggerFactory.getLogger(DataSource.class);

    /** HikariCP 数据源 */
    private final HikariDataSource hikariDS;

    /** 数据库文件路径 */
    private final String dbPath;

    /** 是否已初始化 schema */
    private volatile boolean schemaReady;

    // ==================== 构造 ====================

    /**
     * 私有构造 — 配置 HikariCP 连接池。
     *
     * @param dbPath SQLite 数据库文件路径
     */
    private DataSource(String dbPath) {
        this.dbPath = dbPath;

        HikariConfig config = new HikariConfig();

        // JDBC URL
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);

        // 连接池大小（SQLite 写串行，不宜过大）
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(2);

        // 超时与泄漏检测
        config.setConnectionTimeout(5_000);
        config.setLeakDetectionThreshold(30_000);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(120_000);

        // 连接初始化：每次获取连接时执行 PRAGMA 优化
        config.setConnectionInitSql(
                "PRAGMA journal_mode=WAL;"
                        + "PRAGMA synchronous=NORMAL;"
                        + "PRAGMA foreign_keys=ON;"
                        + "PRAGMA cache_size=-8000;"
                        + "PRAGMA temp_store=MEMORY;"
        );

        // 连接池名称
        config.setPoolName("BRI-SQLite-Pool");

        this.hikariDS = new HikariDataSource(config);
        this.schemaReady = false;
        LOG.info("SQLite 连接池已创建: {} (poolSize={}/{})",
                dbPath, config.getMinimumIdle(), config.getMaximumPoolSize());
    }

    // ==================== 单例访问 ====================

    /**
     * 获取 DataSource 单例。
     *
     * <p>首次调用时创建连接池，后续调用返回同一实例。
     * 注意：必须在使用前调用 {@link #initIfAbsent()} 确保数据库表存在。</p>
     *
     * @param dbPath SQLite 数据库文件路径（仅首次创建时使用，后续调用可传同一值）
     * @return DataSource 单例
     */
    public static DataSource getInstance(String dbPath) {
        if (instance == null) {
            synchronized (DataSource.class) {
                if (instance == null) {
                    instance = new DataSource(dbPath);
                }
            }
        }
        return instance;
    }

    /**
     * 获取已初始化的 DataSource 单例。
     *
     * <p>如果实例尚未通过 {@link #getInstance(String)} 创建，抛出异常。</p>
     *
     * @return DataSource 单例
     * @throws IllegalStateException 如果实例尚未初始化
     */
    public static DataSource getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "DataSource 尚未初始化，请先调用 getInstance(dbPath)");
        }
        return instance;
    }

    // ==================== 数据库初始化 ====================

    /**
     * 幂等初始化数据库（F-02 核心方法）。
     *
     * <p>首次调用时执行：</p>
     * <ol>
     *   <li>创建 events / countries / subregions / downloaded_files 四张表</li>
     *   <li>创建全部性能索引</li>
     *   <li>插入子区域字典种子数据</li>
     * </ol>
     * <p>后续调用通过 {@link SQLiteSchema#isSchemaReady} 快速返回，
     * 不执行任何 DDL，保证零开销。</p>
     *
     * @throws DataSourceException 建表失败时抛出
     */
    public void initIfAbsent() {
        synchronized (this) {
            try (Connection conn = getConnection()) {
                if (SQLiteSchema.isSchemaReady(conn)) {
                    schemaReady = true;
                    // 即使 schema 就绪，也尝试执行迁移（为已有表添加新列）
                    try (var stmt = conn.createStatement()) {
                        stmt.execute(SQLiteSchema.MIGRATE_EVENTS_SOURCE_URL);
                    } catch (SQLException ignored) {
                        // 列已存在则忽略
                    }
                    LOG.debug("Schema 已就绪（所有表已存在），跳过建表");
                    return;
                }
                LOG.info("开始创建数据库 Schema...");
                SQLiteSchema.createAll(conn);
                schemaReady = true;
                LOG.info("数据库 Schema 创建完成（events / countries / subregions / downloaded_files）");
            } catch (SQLException e) {
                throw new DataSourceException(
                        "数据库初始化失败: " + dbPath, e);
            }
        }
    }

    /**
     * 强制重建数据库 schema（仅用于开发/测试环境）。
     *
     * <p><b>警告：此操作会删除所有数据！</b></p>
     *
     * @throws DataSourceException 操作失败时抛出
     */
    public void resetSchema() {
        synchronized (this) {
            try (Connection conn = getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS events");
                stmt.execute("DROP TABLE IF EXISTS countries");
                stmt.execute("DROP TABLE IF EXISTS subregions");
                stmt.execute("DROP TABLE IF EXISTS downloaded_files");
                schemaReady = false;
                SQLiteSchema.createAll(conn);
                schemaReady = true;
            } catch (SQLException e) {
                throw new DataSourceException(
                        "数据库重建失败: " + dbPath, e);
            }
        }
    }

    // ==================== 连接获取 ====================

    /**
     * 从连接池获取一个数据库连接。
     *
     * <p>连接已应用 WAL / synchronous=NORMAL 等优化 PRAGMA。
     * 调用方负责在使用完毕后调用 {@link Connection#close()} 归还连接池。</p>
     *
     * @return 数据库连接
     * @throws SQLException 获取连接失败时抛出
     */
    public Connection getConnection() throws SQLException {
        return hikariDS.getConnection();
    }

    // ==================== 状态查询 ====================

    /**
     * 返回数据库 schema 是否已初始化。
     *
     * @return {@code true} 如果表结构已就绪
     */
    public boolean isSchemaReady() {
        return schemaReady;
    }

    /**
     * 返回数据库文件路径。
     *
     * @return 数据库文件路径
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * 返回连接池活跃连接数。
     *
     * @return 当前活跃连接数
     */
    public int getActiveConnections() {
        return hikariDS.getHikariPoolMXBean().getActiveConnections();
    }

    // ==================== 资源释放 ====================

    /**
     * 关闭连接池，释放所有数据库连接。
     *
     * <p>应在应用退出前调用。关闭后 DataSource 单例重置为 {@code null}，
     * 后续可重新调用 {@link #getInstance(String)} 创建新实例。</p>
     */
    @Override
    public void close() {
        synchronized (DataSource.class) {
            if (hikariDS != null && !hikariDS.isClosed()) {
                hikariDS.close();
            }
            instance = null;
            schemaReady = false;
        }
    }

    // ==================== 异常类 ====================

    /**
     * 数据源异常 — 封装数据库初始化与管理中的错误。
     */
    public static class DataSourceException extends RuntimeException {

        /**
         * 构造数据源异常。
         *
         * @param message 错误描述
         */
        public DataSourceException(String message) {
            super(message);
        }

        /**
         * 构造包含原因的数据库异常。
         *
         * @param message 错误描述
         * @param cause   原始异常
         */
        public DataSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
