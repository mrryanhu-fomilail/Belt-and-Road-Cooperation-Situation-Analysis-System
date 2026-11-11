package com.bri.dao;

import com.bri.config.DataSource;

/**
 * DAO 工厂 — 全模块统一的数据访问入口（单例）。
 *
 * <p>内部持有 {@link DataSource} 连接池，并创建 {@link EventDaoImpl}
 * 和 {@link CountryDaoImpl} 实例。各模块通过本工厂获取 DAO 接口，
 * 无需关心底层实现细节。</p>
 *
 * <h3>使用示例（给队友 — 30 秒上手）</h3>
 * <pre>{@code
 * // ===== 初始化（数据管理模块同学完成） =====
 * DataSource ds = DataSource.getInstance("bri.db");
 * ds.initIfAbsent();
 * new CountryConfigLoader().loadToDatabase(ds);
 * new GdeltImporter().importFile(tsvPath, ds, listener);
 *
 * // ===== 使用（查询/分析/可视化同学直接调用） =====
 * DaoFactory f = DaoFactory.getInstance("bri.db");
 * EventDao eventDao = f.eventDao();
 * CountryDao countryDao = f.countryDao();
 *
 * // 查询检索模块
 * List<Event> list = eventDao.queryBilateral("CHN", "PAK", "2025-01-01", "2025-12-31");
 *
 * // 分析模块
 * Map<String,Integer> trend = eventDao.monthlyCount("KAZ", true);
 *
 * // 可视化模块
 * long coop = eventDao.countEvents(true);
 * long conflict = eventDao.countEvents(false);
 *
 * // 任何模块 — 国家码转中文名
 * Country c = countryDao.getByCode("IRN");
 * System.out.println(c.getNameCn()); // "伊朗"
 * }</pre>
 *
 * <h3>线程安全</h3>
 * <p>单例本身线程安全（双重检查锁）。DAO 实现通过 HikariCP 连接池
 * 每次获取独立连接，天然支持并发调用。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see EventDao
 * @see CountryDao
 * @see DataSource
 */
public class DaoFactory {

    /** 单例实例 */
    private static volatile DaoFactory instance;

    /** 数据源 */
    private final DataSource dataSource;

    /** 事件 DAO */
    private final EventDao eventDao;

    /** 国家 DAO */
    private final CountryDao countryDao;

    // ==================== 构造 ====================

    /**
     * 私有构造 — 初始化数据源和 DAO 实现。
     *
     * @param dbPath SQLite 数据库文件路径
     */
    private DaoFactory(String dbPath) {
        this.dataSource = DataSource.getInstance(dbPath);
        this.eventDao = new EventDaoImpl(dataSource);
        this.countryDao = new CountryDaoImpl(dataSource);
    }

    // ==================== 单例访问 ====================

    /**
     * 获取 DaoFactory 单例。
     *
     * <p>首次调用时初始化连接池和 DAO 实现。注意：
     * 调用前需确保数据库 schema 和数据已就绪（由数据管理模块负责）。</p>
     *
     * @param dbPath SQLite 数据库文件路径，如 "bri.db"
     * @return DaoFactory 单例
     */
    public static DaoFactory getInstance(String dbPath) {
        if (instance == null) {
            synchronized (DaoFactory.class) {
                if (instance == null) {
                    instance = new DaoFactory(dbPath);
                }
            }
        }
        return instance;
    }

    /**
     * 获取已初始化的 DaoFactory 单例。
     *
     * @return DaoFactory 单例
     * @throws IllegalStateException 如果尚未初始化
     */
    public static DaoFactory getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "DaoFactory 尚未初始化，请先调用 getInstance(dbPath)");
        }
        return instance;
    }

    // ==================== DAO 获取 ====================

    /**
     * 获取事件 DAO 接口实例。
     *
     * <p>返回的实例线程安全，可在后台线程中使用。</p>
     *
     * @return EventDao 实现
     */
    public EventDao eventDao() {
        return eventDao;
    }

    /**
     * 获取国家 DAO 接口实例。
     *
     * <p>返回的实例线程安全，可在后台线程中使用。</p>
     *
     * @return CountryDao 实现
     */
    public CountryDao countryDao() {
        return countryDao;
    }

    // ==================== 工具方法 ====================

    /**
     * 获取底层数据源（供导入等内部操作使用）。
     *
     * @return DataSource 实例
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 返回数据库文件路径。
     *
     * @return 数据库文件路径
     */
    public String getDbPath() {
        return dataSource.getDbPath();
    }

    // ==================== 资源释放 ====================

    /**
     * 关闭 DAO 工厂，释放连接池。
     *
     * <p>应在应用退出时调用。关闭后单例重置，
     * 后续可调用 {@link #getInstance(String)} 重新初始化。</p>
     */
    public void close() {
        synchronized (DaoFactory.class) {
            if (dataSource != null) {
                dataSource.close();
            }
            instance = null;
        }
    }
}
