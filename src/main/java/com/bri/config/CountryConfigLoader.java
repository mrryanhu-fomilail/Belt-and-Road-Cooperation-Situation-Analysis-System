package com.bri.config;

import com.bri.model.Country;
import com.bri.model.Subregion;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一带一路沿线国家配置加载器（F-05 核心实现）。
 *
 * <p>从 classpath 下的 {@code bri_countries.json} 资源文件中加载
 * 150+ 一带一路沿线国家元数据，解析后批量写入 countries 表。
 * 支持按子区域过滤查询。</p>
 *
 * <h3>JSON 格式</h3>
 * <p>资源文件为 JSON 数组，每项包含：</p>
 * <pre>{@code
 * {"cameo_code":"KAZ","name_cn":"哈萨克斯坦","name_en":"Kazakhstan","subregion":"CA","is_bri":1}
 * }</pre>
 * <p>支持 {@code //} 单行注释，用于标记 TODO 和分类标题。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DataSource ds = DataSource.getInstance("bri.db");
 * ds.initIfAbsent();
 *
 * // 加载沿线国家配置到数据库
 * CountryConfigLoader loader = new CountryConfigLoader();
 * int count = loader.loadToDatabase(ds);
 * System.out.println("已写入 " + count + " 个国家");
 *
 * // 按子区域过滤
 * List<Country> seaCountries = loader.loadBySubregion("SEA");
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see Country
 * @see Subregion
 * @see DataSource
 */
public class CountryConfigLoader {

    /** classpath 中的沿线国家 JSON 资源文件名 */
    public static final String RESOURCE_PATH = "bri_countries.json";

    /** 批量写入的 INSERT SQL（INSERT OR REPLACE 保证幂等） */
    private static final String INSERT_SQL =
            "INSERT OR REPLACE INTO countries(cameo_code, name_cn, name_en, subregion, is_bri) "
                    + "VALUES (?, ?, ?, ?, ?)";

    /** Jackson 解析器（配置为允许注释） */
    private final ObjectMapper mapper;

    // ==================== 构造 ====================

    /**
     * 创建加载器实例，配置 Jackson 以支持 JSON 注释。
     */
    public CountryConfigLoader() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    // ==================== 资源加载 ====================

    /**
     * 从 classpath 资源文件加载全部国家列表。
     *
     * <p>读取 {@code src/main/resources/bri_countries.json}，
     * Jackson 解析为 {@link Country} 对象列表。
     * 解析失败抛出 {@link IllegalStateException}。</p>
     *
     * @return 国家列表（不可变）
     * @throws IllegalStateException 如果资源文件不存在或 JSON 格式错误
     */
    public List<Country> loadFromResource() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "沿线国家配置文件未找到: " + RESOURCE_PATH
                                + "。请确保 src/main/resources/" + RESOURCE_PATH + " 存在。");
            }
            Country[] countries = mapper.readValue(in, Country[].class);
            List<Country> result = new ArrayList<>(countries.length);
            for (Country c : countries) {
                result.add(c);
            }
            return Collections.unmodifiableList(result);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "沿线国家配置文件解析失败: " + RESOURCE_PATH, e);
        }
    }

    /**
     * 按子区域过滤并返回国家列表。
     *
     * <p>等价于 {@code loadFromResource() + filter by subregion}，
     * 但数据来自资源文件，不会查询数据库。</p>
     *
     * @param subregionKey 子区域键，如 "SEA"、 "CA"
     * @return 该子区域下的国家列表
     * @throws IllegalArgumentException 如果 subregionKey 不是有效的子区域
     * @see Subregion#fromKey(String)
     */
    public List<Country> loadBySubregion(String subregionKey) {
        Subregion sub = Subregion.fromKey(subregionKey);
        if (sub == null) {
            throw new IllegalArgumentException(
                    "无效的子区域键: " + subregionKey
                            + "。有效值: SEA, CA, ME, EE, AF");
        }
        return loadFromResource().stream()
                .filter(c -> subregionKey.equals(c.getSubregion()))
                .collect(Collectors.toUnmodifiableList());
    }

    // ==================== 数据库写入 ====================

    /**
     * 从资源文件加载所有国家并批量写入 countries 表。
     *
     * <p>使用 {@code INSERT OR REPLACE}，重复调用幂等（不会产生重复行）。
     * 写入在单个事务中完成，失败时回滚。</p>
     *
     * @param ds 数据源
     * @return 实际写入的国家数量
     * @throws DataSource.DataSourceException 数据库写入失败时抛出
     */
    public int loadToDatabase(DataSource ds) {
        List<Country> countries = loadFromResource();
        int written = 0;

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                for (Country c : countries) {
                    ps.setString(1, c.getCameoCode());
                    ps.setString(2, c.getNameCn());
                    ps.setString(3, c.getNameEn());
                    ps.setString(4, c.getSubregion());
                    ps.setInt(5, c.getIsBri());
                    ps.addBatch();
                    written++;
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataSource.DataSourceException(
                    "沿线国家数据写入失败，已回滚。共尝试写入 "
                            + written + " 条", e);
        }
        return written;
    }

    /**
     * 直接向数据库写入一个内存中的国家列表（非资源文件）。
     *
     * <p>适用于从其他来源（如 API、手动构造）获取国家数据后入库。
     * 同样使用 {@code INSERT OR REPLACE} 保证幂等。</p>
     *
     * @param ds        数据源
     * @param countries 国家列表
     * @return 写入的数量
     * @throws DataSource.DataSourceException 写入失败时抛出
     */
    public int writeToDatabase(DataSource ds, List<Country> countries) {
        int written = 0;
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                for (Country c : countries) {
                    ps.setString(1, c.getCameoCode());
                    ps.setString(2, c.getNameCn());
                    ps.setString(3, c.getNameEn());
                    ps.setString(4, c.getSubregion());
                    ps.setInt(5, c.getIsBri());
                    ps.addBatch();
                    written++;
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataSource.DataSourceException(
                    "国家数据写入失败，已回滚", e);
        }
        return written;
    }

    // ==================== 统计与校验 ====================

    /**
     * 统计资源文件中各子区域的国家数量。
     *
     * @return 子区域键 → 国家数量的映射
     */
    public Map<String, Long> countBySubregion() {
        return loadFromResource().stream()
                .collect(Collectors.groupingBy(
                        Country::getSubregion,
                        Collectors.counting()));
    }

    /**
     * 校验资源文件格式是否正确（不写入数据库）。
     *
     * <p>返回加载到的国家总数，可用于快速验证 JSON 资源文件是否可解析。</p>
     *
     * @return 资源文件中的国家数量
     */
    public int validate() {
        return loadFromResource().size();
    }
}
