package com.yourteam.bri.service;

import com.yourteam.bri.model.Country;
import com.yourteam.bri.util.BRICountries;

import java.util.*;

/**
 * 配置服务类 - 管理一带一路沿线国家的地理坐标和基础配置信息
 * <p>
 * 内置 150+ 个一带一路沿线国家的经纬度坐标数据，支持：
 * <ul>
 *   <li>获取所有国家列表及坐标（用于地图渲染）</li>
 *   <li>按子区域分组获取国家配置</li>
 *   <li>获取国家中文名称和国旗图标路径</li>
 * </ul>
 * </p>
 * <p>
 * 坐标数据统一来自 {@code BriEngineHolder.getCountryCoords()}，
 * 国家基础信息来自 {@code BRICountries}，避免多源数据维护。
 * </p>
 *
 * @see com.yourteam.bri.util.BRICountries 国家基础信息工具类
 * @see com.yourteam.bri.service.BriEngineHolder#getCountryCoords() 坐标数据源
 * @see com.yourteam.bri.gui.MapPanel 地图面板依赖本类的坐标数据
 */
public class ConfigService {

    /**
     * 获取所有一带一路沿线国家列表（含经纬度坐标和区域信息）
     * <p>
     * 遍历 BRICountries 中的所有国家代码，为每个国家创建 Country 对象，
     * 并从统一坐标源中匹配对应坐标。除中国外，所有国家均标记为一带一路沿线国家。
     * </p>
     *
     * @return 包含完整国家信息（含坐标）的列表
     */
    public List<Country> getCountryList() {
        Map<String, double[]> coords = BriEngineHolder.getCountryCoords();
        List<Country> countries = new ArrayList<>();
        for (String code : BRICountries.getCountryCodes()) {
            double[] latLon = coords.get(code);
            Country country = new Country(
                code,
                BRICountries.getCountryName(code),
                BRICountries.getCountryRegion(code),
                latLon != null ? latLon[0] : 0,
                latLon != null ? latLon[1] : 0,
                !code.equals("CHN")  // 除中国外都是一带一路国家
            );
            countries.add(country);
        }
        return countries;
    }

    /**
     * 获取子区域配置映射
     * <p>
     * 按照"东南亚""中亚""中东""欧洲""非洲""南亚""东亚"等区域，
     * 将各国代码分组整理，用于区域筛选下拉框和区域对比分析。
     * </p>
     *
     * @return Map&lt;区域名称, 该区域国家代码列表&gt;
     */
    public Map<String, List<String>> getRegionConfig() {
        Map<String, List<String>> config = new LinkedHashMap<>();
        for (String region : BRICountries.getMainRegions()) {
            config.put(region, BRICountries.getCountriesByRegion(region));
        }
        config.put("东亚", BRICountries.getCountriesByRegion("东亚"));
        return config;
    }

    /**
     * 根据国家代码获取其中文名称
     *
     * @param code 三字母国家代码（如 "CHN"）
     * @return 国家中文名称（如 "中国"），如果未找到则返回国家代码本身
     */
    public String getChineseName(String code) {
        return BRICountries.getCountryName(code);
    }

    /**
     * 获取国家国旗图标的资源路径
     * <p>
     * 图标文件命名规则：国家代码小写 + ".png"，
     * 存放于 resources/flags/ 目录下。
     * 当前为预留接口，实际图标资源需后续添加。
     * </p>
     *
     * @param code 三字母国家代码（如 "CHN"）
     * @return 资源路径字符串（如 "/flags/chn.png"）
     */
    public String getFlagIconPath(String code) {
        return "/flags/" + code.toLowerCase() + ".png";
    }
}

