package com.bri.analysis;

import com.bri.model.BriCountry;
import java.util.*;

/**
 * 一带一路国家综合信息 — 名称、经纬度、所属区域（硬编码）
 * 用于生成 countries.json
 */
public class CountryInfoProvider {

    private static final Map<String, Object[]> DATA = new LinkedHashMap<>();

    static {
        r("CHN", 35.8617, 104.1954, "中国", "China", "东亚");
        r("BRN", 4.5353, 114.7277, "文莱", "Brunei", "东南亚");
        r("KHM", 12.5657, 104.9910, "柬埔寨", "Cambodia", "东南亚");
        r("IDN", -0.7893, 113.9213, "印度尼西亚", "Indonesia", "东南亚");
        r("LAO", 19.8563, 102.4955, "老挝", "Laos", "东南亚");
        r("MYS", 4.2105, 101.9758, "马来西亚", "Malaysia", "东南亚");
        r("MMR", 21.9162, 95.9560, "缅甸", "Myanmar", "东南亚");
        r("PHL", 12.8797, 121.7740, "菲律宾", "Philippines", "东南亚");
        r("SGP", 1.3521, 103.8198, "新加坡", "Singapore", "东南亚");
        r("THA", 15.8700, 100.9925, "泰国", "Thailand", "东南亚");
        r("TLS", -8.8742, 125.7275, "东帝汶", "Timor-Leste", "东南亚");
        r("VNM", 14.0583, 108.2772, "越南", "Vietnam", "东南亚");
        r("KAZ", 48.0196, 66.9237, "哈萨克斯坦", "Kazakhstan", "中亚");
        r("KGZ", 41.2044, 74.7661, "吉尔吉斯斯坦", "Kyrgyzstan", "中亚");
        r("TJK", 38.8610, 71.2761, "塔吉克斯坦", "Tajikistan", "中亚");
        r("TKM", 38.9697, 59.5563, "土库曼斯坦", "Turkmenistan", "中亚");
        r("UZB", 41.3775, 64.5853, "乌兹别克斯坦", "Uzbekistan", "中亚");
        r("AFG", 33.9391, 67.7100, "阿富汗", "Afghanistan", "中东");
        r("BHR", 26.0275, 50.5500, "巴林", "Bahrain", "中东");
        r("IRN", 32.4279, 53.6880, "伊朗", "Iran", "中东");
        r("IRQ", 33.2232, 43.6793, "伊拉克", "Iraq", "中东");
        r("ISR", 31.0461, 34.8516, "以色列", "Israel", "中东");
        r("JOR", 30.5852, 36.2384, "约旦", "Jordan", "中东");
        r("KWT", 29.3759, 47.9774, "科威特", "Kuwait", "中东");
        r("LBN", 33.8547, 35.8623, "黎巴嫩", "Lebanon", "中东");
        r("OMN", 21.4735, 55.9754, "阿曼", "Oman", "中东");
        r("QAT", 25.3548, 51.1839, "卡塔尔", "Qatar", "中东");
        r("SAU", 23.8859, 45.0792, "沙特阿拉伯", "Saudi Arabia", "中东");
        r("SYR", 34.8021, 38.9968, "叙利亚", "Syria", "中东");
        r("TUR", 38.9637, 35.2433, "土耳其", "Turkey", "中东");
        r("ARE", 23.4241, 53.8478, "阿联酋", "UAE", "中东");
        r("YEM", 15.5527, 48.5164, "也门", "Yemen", "中东");
        r("ALB", 41.1533, 20.1683, "阿尔巴尼亚", "Albania", "欧洲");
        r("ARM", 40.0691, 45.0382, "亚美尼亚", "Armenia", "欧洲");
        r("AZE", 40.1431, 47.5769, "阿塞拜疆", "Azerbaijan", "欧洲");
        r("BLR", 53.7098, 27.9534, "白俄罗斯", "Belarus", "欧洲");
        r("BIH", 43.9159, 17.6791, "波黑", "Bosnia", "欧洲");
        r("BGR", 42.7339, 25.4858, "保加利亚", "Bulgaria", "欧洲");
        r("HRV", 45.1000, 15.2000, "克罗地亚", "Croatia", "欧洲");
        r("CZE", 49.8175, 15.4730, "捷克", "Czech Republic", "欧洲");
        r("EST", 58.5953, 25.0136, "爱沙尼亚", "Estonia", "欧洲");
        r("GEO", 42.3154, 43.3569, "格鲁吉亚", "Georgia", "欧洲");
        r("GRC", 39.0742, 21.8243, "希腊", "Greece", "欧洲");
        r("HUN", 47.1625, 19.5033, "匈牙利", "Hungary", "欧洲");
        r("LVA", 56.8796, 24.6032, "拉脱维亚", "Latvia", "欧洲");
        r("LTU", 55.1694, 23.8813, "立陶宛", "Lithuania", "欧洲");
        r("MDA", 47.4116, 28.3699, "摩尔多瓦", "Moldova", "欧洲");
        r("MNE", 42.7087, 19.3744, "黑山", "Montenegro", "欧洲");
        r("MKD", 41.6086, 21.7453, "北马其顿", "North Macedonia", "欧洲");
        r("POL", 51.9194, 19.1451, "波兰", "Poland", "欧洲");
        r("ROU", 45.9432, 24.9668, "罗马尼亚", "Romania", "欧洲");
        r("RUS", 61.5240, 105.3188, "俄罗斯", "Russia", "欧洲");
        r("SRB", 44.0165, 21.0059, "塞尔维亚", "Serbia", "欧洲");
        r("SVK", 48.6690, 19.6990, "斯洛伐克", "Slovakia", "欧洲");
        r("SVN", 46.1512, 14.9955, "斯洛文尼亚", "Slovenia", "欧洲");
        r("UKR", 48.3794, 31.1656, "乌克兰", "Ukraine", "欧洲");
        r("DZA", 28.0339, 1.6596, "阿尔及利亚", "Algeria", "非洲");
        r("AGO", -11.2027, 17.8739, "安哥拉", "Angola", "非洲");
        r("EGY", 26.8206, 30.8025, "埃及", "Egypt", "非洲");
        r("ETH", 9.1450, 40.4897, "埃塞俄比亚", "Ethiopia", "非洲");
        r("KEN", -1.2921, 36.8219, "肯尼亚", "Kenya", "非洲");
        r("MAR", 31.7917, -7.0926, "摩洛哥", "Morocco", "非洲");
        r("MOZ", -18.6657, 35.5296, "莫桑比克", "Mozambique", "非洲");
        r("NGA", 9.0820, 8.6753, "尼日利亚", "Nigeria", "非洲");
        r("SEN", 14.4974, -14.4524, "塞内加尔", "Senegal", "非洲");
        r("ZAF", -30.5595, 22.9375, "南非", "South Africa", "非洲");
        r("SDN", 12.8628, 30.2176, "苏丹", "Sudan", "非洲");
        r("TZA", -6.3690, 34.8888, "坦桑尼亚", "Tanzania", "非洲");
        r("TUN", 33.8869, 9.5375, "突尼斯", "Tunisia", "非洲");
        r("ZMB", -13.1339, 27.8493, "赞比亚", "Zambia", "非洲");
        r("ZWE", -19.0154, 29.1549, "津巴布韦", "Zimbabwe", "非洲");
        r("GHA", 7.9465, -1.0232, "加纳", "Ghana", "非洲");
        r("LBY", 26.3351, 17.2283, "利比亚", "Libya", "非洲");
        r("DJI", 11.8251, 42.5903, "吉布提", "Djibouti", "非洲");
        r("BWA", -22.3285, 24.6849, "博茨瓦纳", "Botswana", "非洲");
        r("CMR", 7.3697, 12.3547, "喀麦隆", "Cameroon", "非洲");
        r("BGD", 23.6850, 90.3563, "孟加拉国", "Bangladesh", "南亚");
        r("IND", 20.5937, 78.9629, "印度", "India", "南亚");
        r("NPL", 28.3949, 84.1240, "尼泊尔", "Nepal", "南亚");
        r("PAK", 30.3753, 69.3451, "巴基斯坦", "Pakistan", "南亚");
        r("LKA", 7.8731, 80.7718, "斯里兰卡", "Sri Lanka", "南亚");
        r("MDV", 3.2028, 73.2207, "马尔代夫", "Maldives", "南亚");
        r("MNG", 46.8625, 103.8467, "蒙古", "Mongolia", "东亚");
        r("KOR", 35.9078, 127.7669, "韩国", "South Korea", "东亚");

        // ===== 常见非 BRI 国家（GDELT 数据中高频出现，仅提供名称映射） =====
        n("USA", "美国", "United States");
        n("GBR", "英国", "United Kingdom");
        n("DEU", "德国", "Germany");
        n("FRA", "法国", "France");
        n("JPN", "日本", "Japan");
        n("AUS", "澳大利亚", "Australia");
        n("CAN", "加拿大", "Canada");
        n("BRA", "巴西", "Brazil");
        n("ITA", "意大利", "Italy");
        n("ESP", "西班牙", "Spain");
        n("PRT", "葡萄牙", "Portugal");
        n("NLD", "荷兰", "Netherlands");
        n("BEL", "比利时", "Belgium");
        n("CHE", "瑞士", "Switzerland");
        n("SWE", "瑞典", "Sweden");
        n("NOR", "挪威", "Norway");
        n("DNK", "丹麦", "Denmark");
        n("FIN", "芬兰", "Finland");
        n("AUT", "奥地利", "Austria");
        n("IRL", "爱尔兰", "Ireland");
        n("NZL", "新西兰", "New Zealand");
        n("MEX", "墨西哥", "Mexico");
        n("ARG", "阿根廷", "Argentina");
        n("CHL", "智利", "Chile");
        n("COL", "哥伦比亚", "Colombia");
        n("PER", "秘鲁", "Peru");
        n("VEN", "委内瑞拉", "Venezuela");
        n("CUB", "古巴", "Cuba");
        n("PRK", "朝鲜", "North Korea");
        n("TWN", "台湾", "Taiwan");
        n("HKG", "香港", "Hong Kong");
        n("MAC", "澳门", "Macau");
        n("SGP", "新加坡", "Singapore");
        n("URY", "乌拉圭", "Uruguay");
        n("CRI", "哥斯达黎加", "Costa Rica");
        n("PAN", "巴拿马", "Panama");
        n("ISL", "冰岛", "Iceland");
        n("LUX", "卢森堡", "Luxembourg");
        n("MCO", "摩纳哥", "Monaco");
        n("VAT", "梵蒂冈", "Vatican");
        n("FJI", "斐济", "Fiji");
        n("PNG", "巴布亚新几内亚", "Papua New Guinea");
    }

    /** 仅注册名称（无坐标），用于非 BRI 国家的显示名称映射 */
    private static void n(String code, String nameCn, String nameEn) {
        DATA.put(code, new Object[]{0.0, 0.0, nameCn, nameEn, "其他"});
    }

    private static void r(String code, double lat, double lon,
                          String name, String nameEn, String region) {
        DATA.put(code, new Object[]{lat, lon, name, nameEn, region});
    }

    public static double[] getLatLon(String code) {
        Object[] a = DATA.get(code);
        return a == null ? null : new double[]{(double) a[0], (double) a[1]};
    }

    public static String getNameCn(String code) {
        Object[] a = DATA.get(code);
        return a == null ? code : (String) a[2];
    }

    public static String getNameEn(String code) {
        Object[] a = DATA.get(code);
        return a == null ? code : (String) a[3];
    }

    public static String getRegionCn(String code) {
        Object[] a = DATA.get(code);
        return a == null ? "其他" : (String) a[4];
    }

    public static BriCountry getBriCountry(String code) {
        Object[] a = DATA.get(code);
        return a == null ? null : new BriCountry(code, (String) a[2], (String) a[3],
                (String) a[4], (double) a[0], (double) a[1]);
    }

    public static boolean contains(String code) { return DATA.containsKey(code); }

    public static Set<String> allCodes() { return DATA.keySet(); }
}
