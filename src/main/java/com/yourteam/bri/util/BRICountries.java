package com.yourteam.bri.util;

import java.util.*;

/**
 * 一带一路沿线国家工具类 - 提供完整的国家基础信息管理功能
 * <p>
 * 内置 150+ 个一带一路沿线国家的完整列表，涵盖东南亚、中亚、中东、欧洲、非洲、南亚等区域
 * 以及中国（归入东亚）。提供以下核心功能：
 * <ul>
 *   <li>按区域筛选：getCountriesByRegion() 获取指定区域的所有国家代码</li>
 *   <li>名称查询：getCountryName() 通过代码获取中文名称</li>
 *   <li>模糊搜索：searchCountry() 支持按代码或名称模糊匹配</li>
 *   <li>区域管理：getMainRegions() 获取一带一路六大区域列表</li>
 * </ul>
 * </p>
 * <p>
 * 国家信息通过内部类 CountryInfo 封装，包含代码（三字母 ISO）、中文名称、所属区域三个维度。
 * 本类被全项目广泛使用，是其他服务和面板的基础数据源。
 * </p>
 *
 * @see CountryInfo 内部类 - 国家信息封装
 * @see com.yourteam.bri.service.ConfigService 依赖本类的国家信息
 * @see com.yourteam.bri.service.DataService 依赖本类的国家基础数据
 */
public class BRICountries {

    private static final Map<String, CountryInfo> COUNTRY_MAP = new LinkedHashMap<>();

    static {
        // 东南亚 (Southeast Asia) — 一带一路
        addBriCountry("IDN", "印度尼西亚", "东南亚");
        addBriCountry("MYS", "马来西亚", "东南亚");
        addBriCountry("PHL", "菲律宾", "东南亚");
        addBriCountry("SGP", "新加坡", "东南亚");
        addBriCountry("THA", "泰国", "东南亚");
        addBriCountry("VNM", "越南", "东南亚");
        addBriCountry("MMR", "缅甸", "东南亚");
        addBriCountry("LAO", "老挝", "东南亚");
        addBriCountry("KHM", "柬埔寨", "东南亚");
        addBriCountry("BRN", "文莱", "东南亚");
        addBriCountry("TLS", "东帝汶", "东南亚");

        // 中亚 (Central Asia) — 一带一路
        addBriCountry("KAZ", "哈萨克斯坦", "中亚");
        addBriCountry("KGZ", "吉尔吉斯斯坦", "中亚");
        addBriCountry("TJK", "塔吉克斯坦", "中亚");
        addBriCountry("UZB", "乌兹别克斯坦", "中亚");
        addBriCountry("TKM", "土库曼斯坦", "中亚");

        // 中东 (Middle East) — 一带一路
        addBriCountry("IRN", "伊朗", "中东");
        addBriCountry("IRQ", "伊拉克", "中东");
        addBriCountry("TUR", "土耳其", "中东");
        addBriCountry("SAU", "沙特阿拉伯", "中东");
        addBriCountry("ARE", "阿联酋", "中东");
        addBriCountry("QAT", "卡塔尔", "中东");
        addBriCountry("KWT", "科威特", "中东");
        addBriCountry("OMN", "阿曼", "中东");
        addBriCountry("BHR", "巴林", "中东");
        addBriCountry("JOR", "约旦", "中东");
        addBriCountry("LBN", "黎巴嫩", "中东");
        addBriCountry("SYR", "叙利亚", "中东");
        addBriCountry("YEM", "也门", "中东");

        // 中东扩展 — 一带一路
        addBriCountry("PSE", "巴勒斯坦", "中东");
        addBriCountry("ARM", "亚美尼亚", "中东");
        addBriCountry("AZE", "阿塞拜疆", "中东");
        addBriCountry("GEO", "格鲁吉亚", "中东");

        // 欧洲 (Europe) — 一带一路
        addBriCountry("RUS", "俄罗斯", "欧洲");
        addBriCountry("UKR", "乌克兰", "欧洲");
        addBriCountry("BLR", "白俄罗斯", "欧洲");
        addBriCountry("POL", "波兰", "欧洲");
        addBriCountry("CZE", "捷克", "欧洲");
        addBriCountry("SVK", "斯洛伐克", "欧洲");
        addBriCountry("HUN", "匈牙利", "欧洲");
        addBriCountry("ROU", "罗马尼亚", "欧洲");
        addBriCountry("BGR", "保加利亚", "欧洲");
        addBriCountry("SRB", "塞尔维亚", "欧洲");
        addBriCountry("HRV", "克罗地亚", "欧洲");
        addBriCountry("BIH", "波黑", "欧洲");
        addBriCountry("MNE", "黑山", "欧洲");
        addBriCountry("MKD", "北马其顿", "欧洲");
        addBriCountry("ALB", "阿尔巴尼亚", "欧洲");
        addBriCountry("GRC", "希腊", "欧洲");
        addBriCountry("LTU", "立陶宛", "欧洲");
        addBriCountry("LVA", "拉脱维亚", "欧洲");
        addBriCountry("EST", "爱沙尼亚", "欧洲");
        addBriCountry("MDA", "摩尔多瓦", "欧洲");

        // 非洲 (Africa) — 一带一路
        addBriCountry("EGY", "埃及", "非洲");
        addBriCountry("ZAF", "南非", "非洲");
        addBriCountry("NGA", "尼日利亚", "非洲");
        addBriCountry("KEN", "肯尼亚", "非洲");
        addBriCountry("ETH", "埃塞俄比亚", "非洲");
        addBriCountry("TZA", "坦桑尼亚", "非洲");
        addBriCountry("AGO", "安哥拉", "非洲");
        addBriCountry("GHA", "加纳", "非洲");
        addBriCountry("CIV", "科特迪瓦", "非洲");
        addBriCountry("MAR", "摩洛哥", "非洲");
        addBriCountry("DZA", "阿尔及利亚", "非洲");
        addBriCountry("LBY", "利比亚", "非洲");
        addBriCountry("SDN", "苏丹", "非洲");
        addBriCountry("SSD", "南苏丹", "非洲");
        addBriCountry("MOZ", "莫桑比克", "非洲");
        addBriCountry("ZMB", "赞比亚", "非洲");
        addBriCountry("ZWE", "津巴布韦", "非洲");
        addBriCountry("UGA", "乌干达", "非洲");
        addBriCountry("MWI", "马拉维", "非洲");
        addBriCountry("SEN", "塞内加尔", "非洲");
        addBriCountry("MLI", "马里", "非洲");
        addBriCountry("BFA", "布基纳法索", "非洲");
        addBriCountry("NER", "尼日尔", "非洲");
        addBriCountry("TCD", "乍得", "非洲");
        addBriCountry("SOM", "索马里", "非洲");
        addBriCountry("SYC", "塞舌尔", "非洲");
        addBriCountry("DJI", "吉布提", "非洲");
        addBriCountry("MRT", "毛里塔尼亚", "非洲");
        addBriCountry("GIN", "几内亚", "非洲");
        addBriCountry("RWA", "卢旺达", "非洲");
        addBriCountry("BDI", "布隆迪", "非洲");
        addBriCountry("TGO", "多哥", "非洲");
        addBriCountry("BEN", "贝宁", "非洲");
        addBriCountry("SLE", "塞拉利昂", "非洲");
        addBriCountry("LBR", "利比里亚", "非洲");
        addBriCountry("MDG", "马达加斯加", "非洲");
        addBriCountry("MUS", "毛里求斯", "非洲");
        addBriCountry("NAM", "纳米比亚", "非洲");
        addBriCountry("BWA", "博茨瓦纳", "非洲");
        addBriCountry("GAB", "加蓬", "非洲");
        addBriCountry("COG", "刚果共和国", "非洲");
        addBriCountry("COD", "刚果民主共和国", "非洲");
        addBriCountry("CMR", "喀麦隆", "非洲");

        // 南亚 (South Asia) — 一带一路
        addBriCountry("PAK", "巴基斯坦", "南亚");
        addBriCountry("BGD", "孟加拉国", "南亚");
        addBriCountry("LKA", "斯里兰卡", "南亚");
        addBriCountry("NPL", "尼泊尔", "南亚");
        addBriCountry("MDV", "马尔代夫", "南亚");
        addBriCountry("AFG", "阿富汗", "南亚");

        // 欧洲扩展（一带一路）
        addBriCountry("ITA", "意大利", "欧洲");
        addBriCountry("AUT", "奥地利", "欧洲");
        addBriCountry("LUX", "卢森堡", "欧洲");
        addBriCountry("MLT", "马耳他", "欧洲");
        addBriCountry("SVN", "斯洛文尼亚", "欧洲");
        addBriCountry("CYP", "塞浦路斯", "欧洲");

        // 非洲扩展 — 一带一路
        addBriCountry("CPV", "佛得角", "非洲");
        addBriCountry("CAF", "中非共和国", "非洲");
        addBriCountry("COM", "科摩罗", "非洲");
        addBriCountry("GNQ", "赤道几内亚", "非洲");
        addBriCountry("ERI", "厄立特里亚", "非洲");
        addBriCountry("GMB", "冈比亚", "非洲");
        addBriCountry("GNB", "几内亚比绍", "非洲");
        addBriCountry("LSO", "莱索托", "非洲");
        addBriCountry("STP", "圣多美和普林西比", "非洲");
        addBriCountry("TUN", "突尼斯", "非洲");

        // 北美洲（一带一路）
        addBriCountry("ATG", "安提瓜和巴布达", "北美洲");
        addBriCountry("BRB", "巴巴多斯", "北美洲");
        addBriCountry("CRI", "哥斯达黎加", "北美洲");
        addBriCountry("DMA", "多米尼克", "北美洲");
        addBriCountry("DOM", "多米尼加", "北美洲");
        addBriCountry("SLV", "萨尔瓦多", "北美洲");
        addBriCountry("GRD", "格林纳达", "北美洲");
        addBriCountry("HND", "洪都拉斯", "北美洲");
        addBriCountry("JAM", "牙买加", "北美洲");
        addBriCountry("NIC", "尼加拉瓜", "北美洲");
        addBriCountry("PAN", "巴拿马", "北美洲");
        addBriCountry("TTO", "特立尼达和多巴哥", "北美洲");

        // 南美洲（一带一路）
        addBriCountry("BOL", "玻利维亚", "南美洲");
        addBriCountry("ECU", "厄瓜多尔", "南美洲");
        addBriCountry("GUY", "圭亚那", "南美洲");
        addBriCountry("SUR", "苏里南", "南美洲");
        addBriCountry("URY", "乌拉圭", "南美洲");
        addBriCountry("ARG", "阿根廷", "南美洲");
        addBriCountry("BRA", "巴西", "南美洲");
        addBriCountry("CHL", "智利", "南美洲");
        addBriCountry("COL", "哥伦比亚", "南美洲");
        addBriCountry("PER", "秘鲁", "南美洲");
        addBriCountry("VEN", "委内瑞拉", "南美洲");

        // 大洋洲（一带一路）
        addBriCountry("COK", "库克群岛", "大洋洲");
        addBriCountry("FJI", "斐济", "大洋洲");
        addBriCountry("KIR", "基里巴斯", "大洋洲");
        addBriCountry("FSM", "密克罗尼西亚联邦", "大洋洲");
        addBriCountry("NRU", "瑙鲁", "大洋洲");
        addBriCountry("NIU", "纽埃", "大洋洲");
        addBriCountry("PNG", "巴布亚新几内亚", "大洋洲");
        addBriCountry("WSM", "萨摩亚", "大洋洲");
        addBriCountry("SLB", "所罗门群岛", "大洋洲");
        addBriCountry("TON", "汤加", "大洋洲");
        addBriCountry("NZL", "新西兰", "大洋洲");
        addBriCountry("VUT", "瓦努阿图", "大洋洲");

        // 中国本身
        addCountry("CHN", "中国", "东亚");

        // 其他常见国家（非一带一路沿线但在GDELT数据中频繁出现）
        addCountry("USA", "美国", "北美洲");
        addCountry("GBR", "英国", "欧洲");
        addCountry("FRA", "法国", "欧洲");
        addCountry("DEU", "德国", "欧洲");
        addCountry("ITA", "意大利", "欧洲");
        addCountry("ESP", "西班牙", "欧洲");
        addCountry("PRT", "葡萄牙", "欧洲");
        addCountry("NLD", "荷兰", "欧洲");
        addCountry("BEL", "比利时", "欧洲");
        addCountry("CHE", "瑞士", "欧洲");
        addCountry("AUT", "奥地利", "欧洲");
        addCountry("SWE", "瑞典", "欧洲");
        addCountry("NOR", "挪威", "欧洲");
        addCountry("DNK", "丹麦", "欧洲");
        addCountry("FIN", "芬兰", "欧洲");
        addCountry("IRL", "爱尔兰", "欧洲");
        addCountry("JPN", "日本", "东亚");
        addBriCountry("KOR", "韩国", "东亚");
        addCountry("PRK", "朝鲜", "东亚");
        addBriCountry("MNG", "蒙古", "东亚");
        addCountry("AUS", "澳大利亚", "大洋洲");
        addCountry("NZL", "新西兰", "大洋洲");
        addCountry("CAN", "加拿大", "北美洲");
        addCountry("MEX", "墨西哥", "北美洲");
        addCountry("BRA", "巴西", "南美洲");
        addCountry("ARG", "阿根廷", "南美洲");
        addCountry("CHL", "智利", "南美洲");
        addCountry("COL", "哥伦比亚", "南美洲");
        addCountry("PER", "秘鲁", "南美洲");
        addCountry("VEN", "委内瑞拉", "南美洲");
        addBriCountry("CUB", "古巴", "北美洲");
        addCountry("TWN", "台湾", "东亚");
        addCountry("HKG", "香港", "东亚");
        addCountry("MAC", "澳门", "东亚");
        addCountry("VUT", "瓦努阿图", "大洋洲");
        addCountry("CYP", "塞浦路斯", "欧洲");
        addCountry("WST", "西亚地区", "中东");
    }

    private static void addCountry(String code, String name, String region) {
        COUNTRY_MAP.put(code, new CountryInfo(code, name, region, false));
    }

    private static void addBriCountry(String code, String name, String region) {
        COUNTRY_MAP.put(code, new CountryInfo(code, name, region, true));
    }

    /**
     * 获取所有一带一路沿线国家（含中国）的三字母代码集合
     *
     * @return 所有国家代码的不可修改视图 Set
     */
    public static Set<String> getCountryCodes() {
        return COUNTRY_MAP.keySet();
    }

    /**
     * 根据国家代码获取中文名称
     *
     * @param code 三字母国家代码（如 "CHN"）
     * @return 国家中文名称（如 "中国"），如果未找到则直接返回代码本身
     */
    public static String getCountryName(String code) {
        CountryInfo info = COUNTRY_MAP.get(code);
        return info != null ? info.getName() : code;
    }

    /**
     * 根据国家代码获取所属区域名称
     *
     * @param code 三字母国家代码（如 "PAK"）
     * @return 区域名称（如 "南亚"），如果未找到则返回 "未知"
     */
    public static String getCountryRegion(String code) {
        CountryInfo info = COUNTRY_MAP.get(code);
        return info != null ? info.getRegion() : "未知";
    }

    /**
     * 判断国家是否为一带一路沿线国家
     *
     * @param code 三字母国家代码（如 "PAK"）
     * @return 如果是一带一路国家则返回 true
     */
    public static boolean isBriCountry(String code) {
        CountryInfo info = COUNTRY_MAP.get(code);
        return info != null && info.isBri();
    }

    /**
     * 获取所有子区域（含"东亚"）的去重列表
     * <p>
     * 遍历所有已注册国家，收集其所属区域名称并去重。
     * 注意：返回的列表包含"东亚"（仅中国），如需仅返回一带一路沿线六大区域，
     * 可使用 getMainRegions() 方法。
     * </p>
     *
     * @return 所有区域名称的列表（按添加顺序，已去重）
     * @see #getMainRegions() 获取六大主要区域
     */
    public static List<String> getRegions() {
        Set<String> regions = new LinkedHashSet<>();
        for (CountryInfo info : COUNTRY_MAP.values()) {
            regions.add(info.getRegion());
        }
        return new ArrayList<>(regions);
    }

    /**
     * 按子区域筛选获取国家代码列表
     * <p>
     * 根据区域名称过滤，返回该区域下所有国家的三字母代码。
     * 常用于筛选下拉框（如 NewsPanel 的区域筛选、RankingPanel 的区域过滤等）。
     * </p>
     *
     * @param region 区域名称（如 "东南亚"、"中东"），大小写敏感
     * @return 该区域内的国家代码列表
     */
    public static List<String> getCountriesByRegion(String region) {
        List<String> codes = new ArrayList<>();
        for (Map.Entry<String, CountryInfo> entry : COUNTRY_MAP.entrySet()) {
            if (entry.getValue().getRegion().equals(region)) {
                codes.add(entry.getKey());
            }
        }
        return codes;
    }

    /**
     * 按子区域筛选获取国家信息对象列表
     * <p>
     * 与 getCountriesByRegion() 类似，但返回的是包含代码、名称、区域的完整 CountryInfo 对象。
     * 适用于需要同时展示代码和名称的 UI 场景。
     * </p>
     *
     * @param region 区域名称（如 "东南亚"）
     * @return 该区域内的 CountryInfo 列表
     */
    public static List<CountryInfo> getCountryInfosByRegion(String region) {
        List<CountryInfo> infos = new ArrayList<>();
        for (CountryInfo info : COUNTRY_MAP.values()) {
            if (info.getRegion().equals(region)) {
                infos.add(info);
            }
        }
        return infos;
    }

    /**
     * 模糊搜索国家 - 按代码或名称进行不区分大小写的模糊匹配
     * <p>
     * 同时搜索国家代码（如 "CHN"）和中文名称（如 "中国"）两个字段，
     * 只要任一包含关键字即视为匹配。用于搜索输入框的实时过滤功能
     * （如 MapPanel 右侧国家列表的搜索框）。
     * </p>
     *
     * @param keyword 搜索关键字（会转换为小写进行匹配）
     * @return 匹配的国家信息列表
     */
    public static List<CountryInfo> searchCountry(String keyword) {
        List<CountryInfo> results = new ArrayList<>();
        keyword = keyword.toLowerCase();
        for (CountryInfo info : COUNTRY_MAP.values()) {
            if (info.getCode().toLowerCase().contains(keyword) ||
                info.getName().toLowerCase().contains(keyword)) {
                results.add(info);
            }
        }
        return results;
    }

    /**
     * 获取所有一带一路沿线国家（含中国）的完整信息列表
     *
     * @return 所有国家的 CountryInfo 列表（包含代码、名称、区域）
     */
    public static List<CountryInfo> getAllCountries() {
        return new ArrayList<>(COUNTRY_MAP.values());
    }

    /**
     * 获取一带一路主要区域名称列表（不含"东亚"）
     * <p>
     * 固定返回按地理顺序排列的区域：
     * 东南亚、中亚、中东、欧洲、非洲、南亚。
     * 由于中国（CHN）单独归入"东亚"，此列表不包含该区域，
     * 适用于 RankingPanel 区域筛选、RadarPanel 区域对比等场景。
     * </p>
     *
     * @return 区域名称列表
     */
    public static List<String> getMainRegions() {
        List<String> regions = new ArrayList<>();
        regions.add("东南亚");
        regions.add("中亚");
        regions.add("中东");
        regions.add("欧洲");
        regions.add("非洲");
        regions.add("南亚");
        return regions;
    }

    /**
     * 国家信息内部类 - 封装单条国家记录的核心信息
     * <p>
     * 包含三字母 ISO 代码、中文名称、所属区域三个不可变字段。
     * toString() 格式为 "名称 (代码)"，方便直接用于 UI 展示。
     * </p>
     */
    public static class CountryInfo {
        /** 国家三字母 ISO 代码（如 "CHN"）*/
        private final String code;
        /** 国家中文名称（如 "中国"）*/
        private final String name;
        /** 所属区域（如 "东南亚""中东"）*/
        private final String region;
        /** 是否是一带一路沿线国家 */
        private final boolean bri;

        public CountryInfo(String code, String name, String region) {
            this(code, name, region, false);
        }

        public CountryInfo(String code, String name, String region, boolean bri) {
            this.code = code;
            this.name = name;
            this.region = region;
            this.bri = bri;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getRegion() { return region; }
        public boolean isBri() { return bri; }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }
}
