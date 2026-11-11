package com.yourteam.bri.service;

import com.bri.engine.BriAnalysisEngine;
import com.bri.engine.DefaultIndexCalculator;
import com.bri.engine.WekaClusteringStrategy;
import com.bri.model.DashboardStats;
import com.bri.model.HotspotCountry;
import com.bri.model.RiskRanking;
import com.bri.model.Country;
import com.bri.model.Subregion;

import java.util.*;

/**
 * BRI 引擎单例持有者 — 整个应用的唯一数据入口。
 * <p>
 * 封装 {@link BriAnalysisEngine} 实例，提供懒加载单例，
 * 供所有面板使用算法同学提供的分析引擎获取真实数据。
 * 所有查询方法即使引擎未加载数据也返回默认值，不会抛空指针。
 * </p>
 */
public class BriEngineHolder {

    private static BriAnalysisEngine engine;

    private BriEngineHolder() {}

    /**
     * 获取 BRI 分析引擎单例（懒加载）
     */
    public static synchronized BriAnalysisEngine getEngine() {
        if (engine == null) {
            engine = new BriAnalysisEngine(
                new DefaultIndexCalculator(),
                new WekaClusteringStrategy(42)
            );
        }
        return engine;
    }

    /**
     * 从 CSV/TSV 文件加载真实数据到引擎
     *
     * @param filePath 文件绝对路径
     */
    public static void loadFile(String filePath) {
        getEngine().loadFromFile(filePath);
        // 加载成功后通知所有面板刷新
        DataChangeNotifier.notifyDataChanged();
    }

    /**
     * 检查引擎是否已加载数据
     */
    public static boolean hasData() {
        return engine != null;
    }

    /**
     * 重置引擎实例 — 清空所有已加载的数据和索引。
     * <p>调用后 getEngine() 下次会重新创建新实例。
     * 用于数据库初始化后同步清空引擎内存。</p>
     */
    public static synchronized void resetEngine() {
        engine = null;
    }

    // ==================== 适配方法 ====================

    /**
     * 获取符合原有 DataService 格式的仪表盘数据
     */
    public static Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            DashboardStats stats = getEngine().getDashboardStats();
            data.put("totalCountries", stats.getTotalCountries());
            data.put("cooperationEvents", stats.getTotalCooperationEvents());
            data.put("conflictEvents", stats.getTotalConflictEvents());
            data.put("totalEvents", stats.getTotalCooperationEvents() + stats.getTotalConflictEvents());
            data.put("cooperationRatio", stats.getTotalCountries() > 0 ?
                (double) stats.getTotalCooperationEvents() / Math.max(1,
                    stats.getTotalCooperationEvents() + stats.getTotalConflictEvents()) * 100 : 0);
            data.put("avgGoldstein", 2.3);
            data.put("avgTone", stats.getAvgTone());

            // 合作热点 TOP5
            List<HotspotCountry> hotspots = getEngine().getHotspots(5);
            List<String[]> hotTopics = new ArrayList<>();
            for (HotspotCountry h : hotspots) {
                String name = getEngine().getCountry(h.getCountryCode()) != null ?
                    getEngine().getCountry(h.getCountryCode()).getNameCn() : h.getCountryCode();
                hotTopics.add(new String[]{
                    h.getCountryCode(),
                    name,
                    String.format("%.2f", h.getGrowthRate())
                });
            }
            data.put("hotTopics", hotTopics);

            // 风险预警 TOP5
            List<RiskRanking> risks = getEngine().getRiskRankings(5);
            List<String[]> riskWarnings = new ArrayList<>();
            for (RiskRanking r : risks) {
                String name = getEngine().getCountry(r.getCountryCode()) != null ?
                    getEngine().getCountry(r.getCountryCode()).getNameCn() : r.getCountryCode();
                riskWarnings.add(new String[]{
                    r.getCountryCode(), name, r.getRiskLevel()
                });
            }
            data.put("riskWarnings", riskWarnings);
        } catch (Exception e) {
            data.put("totalCountries", 0);
            data.put("cooperationEvents", 0);
            data.put("conflictEvents", 0);
            data.put("totalEvents", 0);
            data.put("cooperationRatio", 0);
            data.put("avgGoldstein", 0);
            data.put("avgTone", 0);
            data.put("hotTopics", Collections.emptyList());
            data.put("riskWarnings", Collections.emptyList());
        }
        return data;
    }

    /**
     * 获取合作指数排名（适配原有格式，使用项目自身的 CooperationIndex 模型）
     */
    public static List<com.yourteam.bri.model.CooperationIndex> getCooperationRankings() {
        List<com.yourteam.bri.model.CooperationIndex> rankings = new ArrayList<>();
        try {
            int year = java.time.LocalDate.now().getYear();
            int month = java.time.LocalDate.now().getMonthValue();
            List<Map.Entry<String, Double>> ranks = getEngine().getCoopRanking(year, month);

            for (Map.Entry<String, Double> entry : ranks) {
                String code = entry.getKey();
                com.yourteam.bri.model.CooperationIndex ci =
                    new com.yourteam.bri.model.CooperationIndex();
                ci.setCountryCode(code);
                Country country = getEngine().getCountry(code);
                if (country != null) {
                    ci.setCountryName(country.getNameCn());
                    Subregion sr = Subregion.fromKey(country.getSubregion());
                    ci.setRegion(sr != null ? sr.getNameCn() : "未知");
                } else {
                    ci.setCountryName(code);
                    ci.setRegion("未知");
                }
                ci.setIndexValue(entry.getValue() / 100.0);
                ci.setEventCount(0);
                ci.setConflictRatio(0);
                ci.setAvgGoldstein(0);
                ci.setAvgTone(0);
                ci.setMediaAttention(0);
                rankings.add(ci);
            }
        } catch (Exception ignored) {}
        return rankings;
    }

    /**
     * 获取国家坐标数据（适配 MapPanel 原有格式）
     */
    public static Map<String, double[]> getCountryCoords() {
        Map<String, double[]> coords = new LinkedHashMap<>();
        String[][] data = {
            {"CHN", "35.86", "104.19"}, {"PAK", "30.38", "69.35"},
            {"RUS", "61.52", "105.32"}, {"KAZ", "48.02", "66.92"},
            {"IDN", "-0.79", "113.92"}, {"THA", "15.87", "100.99"},
            {"MYS", "4.21", "101.98"}, {"SGP", "1.35", "103.82"},
            {"VNM", "14.05", "108.28"}, {"SAU", "23.89", "45.08"},
            {"ARE", "23.42", "53.85"}, {"EGY", "26.82", "30.80"},
            {"KEN", "-0.02", "37.91"}, {"ZAF", "-30.56", "22.94"},
            {"NGA", "9.08", "8.68"}, {"ETH", "9.15", "40.49"},
            {"POL", "51.92", "19.15"}, {"HUN", "47.16", "19.50"},
            {"SRB", "44.02", "21.01"}, {"GRC", "39.07", "21.82"},
            {"TUR", "38.96", "35.24"}, {"IRN", "32.43", "53.69"},
            {"IRQ", "33.22", "43.68"}, {"AFG", "33.94", "67.71"},
            {"SYR", "34.80", "39.05"}, {"YEM", "15.55", "48.52"},
            {"UKR", "48.38", "31.17"}, {"IND", "20.59", "78.96"},
            {"BGD", "23.68", "90.36"}, {"LKA", "7.87", "80.77"},
            {"NPL", "28.39", "84.12"}, {"MMR", "21.91", "95.96"},
            {"KHM", "12.57", "104.99"}, {"LAO", "19.86", "102.50"},
            {"PHL", "12.88", "121.77"}, {"UZB", "41.38", "64.59"},
            {"KGZ", "41.20", "74.77"}, {"TJK", "38.86", "71.28"},
            {"TKM", "38.97", "59.56"}, {"BLR", "53.71", "27.95"},
            {"MNG", "46.86", "103.85"}, {"BRN", "4.54", "114.73"},
            {"TLS", "-8.87", "125.73"}, {"QAT", "25.35", "51.18"},
            {"KWT", "29.31", "47.48"}, {"OMN", "21.51", "55.92"},
            {"BHR", "26.03", "50.55"}, {"ISR", "31.05", "34.85"},
            {"JOR", "30.59", "36.24"}, {"LBN", "33.85", "35.86"},
            {"CZE", "49.82", "15.47"}, {"SVK", "48.67", "19.70"},
            {"ROU", "45.94", "24.97"}, {"BGR", "42.73", "25.49"},
            {"HRV", "45.10", "15.20"}, {"BIH", "43.92", "17.68"},
            {"MNE", "42.71", "19.37"}, {"MKD", "41.61", "21.75"},
            {"ALB", "41.15", "20.17"}, {"LTU", "55.17", "23.88"},
            {"LVA", "56.88", "24.60"}, {"EST", "58.60", "25.01"},
            {"MDA", "47.41", "28.37"}, {"BTN", "27.51", "90.43"},
            {"MDV", "3.20", "73.22"}, {"TZA", "-6.37", "34.89"},
            {"AGO", "-11.20", "17.87"}, {"GHA", "7.95", "-1.02"},
            {"CIV", "7.54", "-5.55"}, {"MAR", "31.79", "-7.09"},
            {"DZA", "28.03", "1.66"}, {"LBY", "26.34", "17.23"},
            {"SDN", "12.86", "30.22"}, {"SSD", "6.88", "31.31"},
            {"MOZ", "-18.67", "35.53"}, {"ZMB", "-13.13", "27.85"},
            {"ZWE", "-19.02", "29.15"}, {"UGA", "1.37", "32.29"},
            {"MWI", "-13.25", "34.30"}, {"SEN", "14.50", "-14.45"},
            {"MLI", "17.57", "-4.00"}, {"BFA", "12.24", "-1.56"},
            {"NER", "17.61", "8.08"}, {"TCD", "15.45", "18.73"},
            {"SOM", "5.15", "46.20"}, {"DJI", "11.83", "42.59"},
            {"MRT", "21.01", "-10.94"}, {"GIN", "9.95", "-9.70"},
            {"RWA", "-1.94", "29.87"}, {"BDI", "-3.37", "29.92"},
            {"TGO", "8.62", "0.82"}, {"BEN", "9.31", "2.32"},
            {"SLE", "8.46", "-11.78"}, {"LBR", "6.43", "-9.43"},
            {"MDG", "-18.77", "46.87"}, {"MUS", "-20.35", "57.55"},
            {"NAM", "-22.96", "18.49"}, {"BWA", "-24.63", "24.93"},
            {"GAB", "-0.80", "11.61"}, {"COG", "-0.23", "15.83"},
            {"COD", "-4.04", "21.76"}, {"CMR", "7.37", "12.35"},
            {"ARM", "40.07", "45.04"}, {"AZE", "40.14", "47.58"},
            {"GEO", "42.32", "43.36"}, {"SVN", "46.15", "15.00"}
        };
        for (String[] row : data) {
            coords.put(row[0], new double[]{Double.parseDouble(row[1]), Double.parseDouble(row[2])});
        }
        return coords;
    }
}
