package com.bri.dao;

import com.bri.model.Country;
import java.util.List;
import java.util.Map;

/**
 * 国家/配置数据访问接口 — 对外暴露的国家查询与子区域配置契约。
 *
 * <p>所有方法线程安全。界面显示中文名时通过 {@link #getByCode} 转换。</p>
 *
 * <h3>功能覆盖</h3>
 * <ul>
 *   <li>F-05 沿线国家列表 — {@link #listBriCountries()}</li>
 *   <li>F-05 按子区域筛选 — {@link #listBySubregion}</li>
 *   <li>F-11 模糊搜索 — {@link #fuzzySearch}</li>
 *   <li>国家码→中文名转换 — {@link #getByCode}</li>
 *   <li>子区域字典 — {@link #subregionNames()}</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.bri.model.Country
 * @see DaoFactory
 */
public interface CountryDao {

    /**
     * 获取所有一带一路沿线国家列表（F-05）。
     *
     * @return 沿线国家列表
     * @throws DataAccessException 数据库异常
     */
    List<Country> listBriCountries();

    /**
     * 按子区域筛选沿线国家（F-05）。
     *
     * @param key 子区域键，如 "SEA"
     * @return 该子区域下的国家列表
     * @throws DataAccessException 数据库异常
     */
    List<Country> listBySubregion(String key);

    /**
     * 根据 CAMEO 码查询单个国家。
     *
     * @param cameoCode CAMEO 三位码
     * @return 匹配的国家；不存在返回 {@code null}
     * @throws DataAccessException 数据库异常
     */
    Country getByCode(String cameoCode);

    /**
     * 模糊搜索国家（F-11）。
     * <p>按中文名或英文名进行模糊匹配，支持自动补全。</p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的国家列表
     * @throws DataAccessException 数据库异常
     */
    List<Country> fuzzySearch(String keyword);

    /**
     * 获取子区域字典映射。
     *
     * @return key（如 "SEA"）→ 中文名（如 "东南亚"）的映射
     * @throws DataAccessException 数据库异常
     */
    Map<String, String> subregionNames();
}
