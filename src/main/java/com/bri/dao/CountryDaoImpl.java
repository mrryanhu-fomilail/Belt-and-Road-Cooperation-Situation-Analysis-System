package com.bri.dao;

import com.bri.config.DataSource;
import com.bri.model.Country;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * {@link CountryDao} 的 JDBC 实现 — 国家/配置数据访问。
 *
 * <p>所有方法线程安全。各方法独立从连接池获取/归还连接。</p>
 *
 * <h3>使用示例（队友参考）</h3>
 * <pre>{@code
 * DaoFactory f = DaoFactory.getInstance("bri.db");
 * CountryDao dao = f.countryDao();
 *
 * // F-05 所有沿线国家
 * List<Country> all = dao.listBriCountries();
 *
 * // F-05 按子区域
 * List<Country> sea = dao.listBySubregion("SEA");
 *
 * // 国家码→中文名
 * Country c = dao.getByCode("PAK");
 * String name = c.getNameCn();  // "巴基斯坦"
 *
 * // F-11 模糊搜索
 * List<Country> results = dao.fuzzySearch("哈萨克");
 *
 * // 子区域字典
 * Map<String,String> map = dao.subregionNames();
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see CountryDao
 * @see Country
 */
public class CountryDaoImpl implements CountryDao {

    /** 数据源 */
    private final DataSource ds;

    /**
     * 构造 DAO 实现。
     *
     * @param ds 数据库连接池
     */
    public CountryDaoImpl(DataSource ds) {
        this.ds = Objects.requireNonNull(ds, "DataSource 不能为 null");
    }

    // ==================== F-05: 沿线国家列表 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Country> listBriCountries() {
        return queryCountries(
                "SELECT * FROM countries WHERE is_bri = 1 ORDER BY cameo_code",
                Collections.emptyList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Country> listBySubregion(String key) {
        Objects.requireNonNull(key, "子区域键不能为 null");
        return queryCountries(
                "SELECT * FROM countries WHERE subregion = ? AND is_bri = 1 ORDER BY cameo_code",
                List.of(key));
    }

    // ==================== 国家码查询 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Country getByCode(String cameoCode) {
        Objects.requireNonNull(cameoCode, "CAMEO 码不能为 null");
        List<Country> list = queryCountries(
                "SELECT * FROM countries WHERE cameo_code = ?",
                List.of(cameoCode));
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== F-11: 模糊搜索 ====================

    /**
     * {@inheritDoc}
     *
     * <p>同时匹配中文名和英文名。使用 {@code LIKE '%keyword%'} 实现。
     * keyword 为 null 或空字符串时返回空列表。</p>
     */
    @Override
    public List<Country> fuzzySearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String pattern = "%" + keyword.trim() + "%";
        return queryCountries(
                "SELECT * FROM countries"
                        + " WHERE (name_cn LIKE ? OR name_en LIKE ?)"
                        + "   AND is_bri = 1"
                        + " ORDER BY cameo_code",
                List.of(pattern, pattern));
    }

    // ==================== 子区域字典 ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> subregionNames() {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT key, name_cn FROM subregions ORDER BY key")) {
            while (rs.next()) {
                result.put(rs.getString("key"), rs.getString("name_cn"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("子区域字典查询失败", e);
        }
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 执行参数化查询并映射为 Country 列表。
     */
    private List<Country> queryCountries(String sql, List<Object> params) {
        List<Country> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "国家查询失败", e);
        }
        return result;
    }

    /**
     * 将 ResultSet 当前行映射为 Country 对象。
     */
    static Country mapRow(ResultSet rs) throws SQLException {
        Country c = new Country();
        c.setCameoCode(rs.getString("cameo_code"));
        c.setNameCn(rs.getString("name_cn"));
        c.setNameEn(rs.getString("name_en"));
        c.setSubregion(rs.getString("subregion"));
        c.setIsBri(rs.getInt("is_bri"));
        return c;
    }
}
