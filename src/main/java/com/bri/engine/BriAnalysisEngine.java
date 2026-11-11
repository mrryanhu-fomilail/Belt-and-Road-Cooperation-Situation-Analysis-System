package com.bri.engine;

import com.bri.model.*;
import com.bri.output.JsonOutputGenerator;

import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.util.*;

/**
 * BRI 分析引擎门面 — 前端/后端唯一入口。
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>一次 load，终生 O(1) 查询</li>
 *   <li>前端不调 CSV、不调 DB、不调文件 —— 只调这个引擎的方法</li>
 *   <li>JSON 导出变成辅助功能，不是主要交付形式</li>
 * </ul>
 *
 * <h3>前端使用示例（30秒上手）</h3>
 * <pre>{@code
 * // === 初始化（启动时做一次） ===
 * BriAnalysisEngine engine = new BriAnalysisEngine(
 *     new DefaultIndexCalculator(),
 *     new WekaClusteringStrategy(42)
 * );
 * engine.loadFromDirectory("./data/sample/", "*.export.CSV");
 *
 * // === 实时查询（O(1)） ===
 * // 问：巴基斯坦今年合作指数？
 * double idx = engine.getCoopIndex("PAK", 2026, 6);
 * System.out.println("巴基斯坦合作指数: " + idx);
 *
 * // 问：中巴关系趋势？
 * List<Double> trend = engine.getTrend("CHN", "PAK", 2020, 2026);
 * chart.drawLine(trend);  // 前端画折线图
 *
 * // 问：哪些国家风险高？
 * List<RiskRanking> top5 = engine.getRiskRankings(5);
 *
 * // 问：所有国家分成哪几类？
 * List<ClusterResult> clusters = engine.getClusters();
 * map.colorByCluster(clusters);  // 前端给地图上色
 *
 * // 问：导出 JSON（适合离线报告）
 * engine.exportJson("./output/");
 * }</pre>
 *
 * <h3>时间复杂度</h3>
 * <table>
 *   <tr><th>操作</th><th>复杂度</th><th>说明</th></tr>
 *   <tr><td>load</td><td>O(N)</td><td>一次性，仅启动时执行</td></tr>
 *   <tr><td>getCoopIndex</td><td>O(1)</td><td>HashMap 查找</td></tr>
 *   <tr><td>getTrend</td><td>O(Y)</td><td>Y=年份跨度(≤10)</td></tr>
 *   <tr><td>getClusters</td><td>O(1)</td><td>直接返回预计算列表</td></tr>
 *   <tr><td>getRiskRankings</td><td>O(1)</td><td>返回排序好的切片</td></tr>
 *   <tr><td>exportJson</td><td>O(M)</td><td>遍历预计算数据写文件</td></tr>
 * </table>
 */
public class BriAnalysisEngine {

    // ---- 策略（可注入替换） ----
    private final IndexCalculator calculator;
    private final ClusteringStrategy clusteringStrategy;

    // ---- 预计算索引 ----
    private PrecomputedIndex index;

    // ---- 国家列表 ----
    private List<Country> countries;
    private Map<String, Country> countryMap;
    private Set<String> briCodes;

    // ---- 原始事件（按需保留，用于双边/详细查询） ----
    private final Map<String, List<Event>> bilateralEvents = new HashMap<>();

    /**
     * @param calculator          指数计算策略（可注入不同公式）
     * @param clusteringStrategy  聚类策略（可注入不同算法）
     */
    public BriAnalysisEngine(IndexCalculator calculator, ClusteringStrategy clusteringStrategy) {
        this.calculator = calculator;
        this.clusteringStrategy = clusteringStrategy;
    }

    // ==================== 加载阶段 ====================

    /**
     * 加载目录下所有 GDELT CSV 文件，建立预计算索引。
     *
     * @param directory 数据目录
     * @param pattern   文件匹配，如 "*.export.CSV"
     */
    public void loadFromDirectory(String directory, String pattern) {
        loadCountries();
        DataPipeline pipeline = new DataPipeline(briCodes, calculator);
        Map<String, List<Event>> partitioned = pipeline.processDirectory(directory, pattern);

        System.out.println("  → 扫描行数: " + pipeline.getTotalLines()
                + "  有效: " + pipeline.getTotalKept()
                + "  跳过: " + pipeline.getTotalSkipped());

        buildIndexFromPartition(pipeline, partitioned);
    }

    /**
     * 加载单个 CSV 文件。
     */
    public void loadFromFile(String csvPath) {
        loadCountries();
        DataPipeline pipeline = new DataPipeline(briCodes, calculator);
        Map<String, List<Event>> partitioned = pipeline.processFile(csvPath);

        System.out.println("  → 扫描行数: " + pipeline.getTotalLines()
                + "  有效: " + pipeline.getTotalKept()
                + "  跳过: " + pipeline.getTotalSkipped());

        buildIndexFromPartition(pipeline, partitioned);
    }

    private void buildIndexFromPartition(DataPipeline pipeline, Map<String, List<Event>> partitioned) {
        // 聚合为预计算索引
        Map<String, MonthlyStats> stats = pipeline.aggregate(partitioned);

        // 保存双边事件（用于双边趋势查询）
        for (Map.Entry<String, List<Event>> e : partitioned.entrySet()) {
            bilateralEvents.put(e.getKey(), e.getValue());
        }

        index = new PrecomputedIndex(countries, calculator, clusteringStrategy);
        index.putAllStats(stats);
        index.buildDerived();

        System.out.println("  → 索引构建完成: " + stats.size() + " 个聚合条目, "
                + index.clusters.size() + " 国聚类");
    }

    /**
     * 从数据库中加载已有事件数据到引擎（重启后保留之前导入的数据）。
     * <p>
     * 遍历事件列表，使用 DataPipeline 的路由逻辑按国家#月份分区，然后构建索引。
     * </p>
     *
     * @param events 从数据库查询到的事件列表
     */
    public void loadFromEvents(List<Event> events) {
        loadCountries();
        DataPipeline pipeline = new DataPipeline(briCodes, calculator);
        Map<String, List<Event>> partitioned = new HashMap<>();

        int kept = 0, skipped = 0;
        for (Event e : events) {
            if (e == null || e.getEventDate() == null) { skipped++; continue; }
            routeToPartition(partitioned, e, e.getActor1Code(), e.getEventDate());
            String a2 = e.getActor2Code();
            if (a2 != null && !a2.equals(e.getActor1Code())) {
                routeToPartition(partitioned, e, a2, e.getEventDate());
            }
            kept++;
        }

        System.out.println("  → 从数据库加载: " + events.size() + " 条, 有效: " + kept + ", 跳过: " + skipped);
        buildIndexFromPartition(pipeline, partitioned);
    }

    /** 路由一条事件到对应的分区 */
    private void routeToPartition(Map<String, List<Event>> map, Event e, String countryCode, String dateStr) {
        if (countryCode == null || countryCode.isEmpty()) return;
        // 允许一带一路国家以及中国（CHN）作为参与者
        if (!briCodes.contains(countryCode) && !"CHN".equals(countryCode)) return;
        if (dateStr == null || dateStr.length() < 7) return;

        String monthKey = dateStr.substring(0, 7);
        String partition = MonthlyStats.key(countryCode, monthKey);

        map.computeIfAbsent(partition, k -> new ArrayList<>(64))
           .add(e);
    }

    private void loadCountries() {
        countries = new ArrayList<>();
        countryMap = new HashMap<>();
        briCodes = new HashSet<>();

        // 优先从数据库加载国家元数据（包含所有一带一路国家及中国）
        try {
            com.bri.dao.DaoFactory df = com.bri.dao.DaoFactory.getInstance();
            List<com.bri.model.Country> dbCountries = df.countryDao().listBriCountries();
            if (dbCountries != null && !dbCountries.isEmpty()) {
                for (com.bri.model.Country c : dbCountries) {
                    countries.add(c);
                    countryMap.put(c.getCameoCode(), c);
                    briCodes.add(c.getCameoCode());
                }
                // 数据库有 CHN 则加入，没有则手动添加
                if (!briCodes.contains("CHN")) {
                    com.bri.model.Country chn = new com.bri.model.Country("CHN", "中国", "China", "EA", 0);
                    countries.add(chn);
                    countryMap.put("CHN", chn);
                    // 不加入 briCodes（中国不是一带一路沿线国）
                }
                return;
            }
        } catch (Exception ignored) {
            // 数据库不可用时回退到 JSON
        }

        // 从 classpath 读取 bri_countries.json
        try (java.io.InputStream in = BriAnalysisEngine.class.getClassLoader()
                .getResourceAsStream("bri_countries.json")) {
            if (in != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
                com.bri.model.Country[] arr = mapper.readValue(in, com.bri.model.Country[].class);
                for (com.bri.model.Country c : arr) {
                    countries.add(c);
                    countryMap.put(c.getCameoCode(), c);
                    briCodes.add(c.getCameoCode());
                }
                return;
            }
        } catch (Exception ignored) {}

        // fallback：从内置数据构建
        for (String code : com.bri.analysis.CountryInfoProvider.allCodes()) {
            if ("CHN".equals(code)) continue;
            String region = com.bri.analysis.CountryInfoProvider.getRegionCn(code);
            String subregionKey = regionToKey(region);
            com.bri.model.Country c = new com.bri.model.Country(code,
                    com.bri.analysis.CountryInfoProvider.getNameCn(code),
                    com.bri.analysis.CountryInfoProvider.getNameEn(code), subregionKey, 1);
            countries.add(c);
            countryMap.put(code, c);
            briCodes.add(code);
        }
    }

    // ==================== 实时查询 API（前端直接调用） ====================

    /** 查某国某月的合作指数 O(1) */
    public double getCoopIndex(String countryCode, int year, int month) {
        MonthlyStats s = index.statsByMonth.get(MonthlyStats.key(countryCode, year, month));
        return s != null ? s.coopIndex : 50.0; // 无数据返回中性值
    }

    /** 查某国某月的完整月度统计 O(1) */
    public MonthlyStats getMonthlyStats(String countryCode, int year, int month) {
        if (index == null) return new MonthlyStats(50, 0, 0, 0, 0, 0, Collections.emptyMap());
        MonthlyStats s = index.statsByMonth.get(MonthlyStats.key(countryCode, year, month));
        return s != null ? s : new MonthlyStats(50, 0, 0, 0, 0, 0, Collections.emptyMap());
    }

    /** 查某国某年的平均合作指数 O(1) */
    public double getYearlyAvgCoop(String countryCode, int year) {
        double sum = 0;
        int count = 0;
        for (int m = 1; m <= 12; m++) {
            MonthlyStats s = index.statsByMonth.get(MonthlyStats.key(countryCode, year, m));
            if (s != null) { sum += s.coopIndex; count++; }
        }
        return count > 0 ? sum / count : 50.0;
    }

    /** 获取某国的月度合作指数时间序列 O(Y×12) Y=年份跨度 */
    public List<Double> getMonthlySeries(String countryCode, int fromYear, int toYear) {
        List<Double> series = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) {
            for (int m = 1; m <= 12; m++) {
                MonthlyStats s = index.statsByMonth.get(MonthlyStats.key(countryCode, y, m));
                series.add(s != null ? s.coopIndex : Double.NaN);
            }
        }
        return series;
    }

    /** 获取某国合作指数趋势折线数据（含同比、环比） */
    public List<Double> getTrend(String countryCode, int fromYear, int toYear) {
        return getMonthlySeries(countryCode, fromYear, toYear);
    }

    /** 获取双边月度合作指数时间序列 */
    public List<Double> getBilateralTrend(String country1, String country2, int fromYear, int toYear) {
        List<Double> series = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) {
            for (int m = 1; m <= 12; m++) {
                String key = MonthlyStats.key(country1 + "-" + country2, y, m);
                MonthlyStats s = index.statsByMonth.get(key);
                series.add(s != null ? s.coopIndex : Double.NaN);
            }
        }
        return series;
    }

    /** 合作指数排名 O(C log C) C=国家数≤80 */
    public List<Map.Entry<String, Double>> getCoopRanking(int year, int month) {
        Map<String, Double> scores = new HashMap<>();
        for (Country c : countries) {
            scores.put(c.getCameoCode(), getCoopIndex(c.getCameoCode(), year, month));
        }
        List<Map.Entry<String, Double>> ranking = new ArrayList<>(scores.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return ranking;
    }

    /** 获取所有聚类结果 O(1) */
    public List<ClusterResult> getClusters() {
        return Collections.unmodifiableList(index.clusters);
    }

    /** 获取风险排名前 N 名 O(1) */
    public List<RiskRanking> getRiskRankings(int topN) {
        if (index.rankings.size() <= topN) return new ArrayList<>(index.rankings);
        return new ArrayList<>(index.rankings.subList(0, topN));
    }

    /** 获取热点国家前 N 名 O(1) */
    public List<HotspotCountry> getHotspots(int topN) {
        if (index.hotspots.size() <= topN) return new ArrayList<>(index.hotspots);
        return new ArrayList<>(index.hotspots.subList(0, topN));
    }

    /** 获取国家列表 O(1) */
    public List<Country> getCountries() {
        return Collections.unmodifiableList(countries);
    }

    /** 获取某国信息 O(1) */
    public Country getCountry(String code) {
        return countryMap.get(code);
    }

    /** 获取全部子区域名称 O(1) */
    public List<String> getRegions() {
        Set<String> set = new LinkedHashSet<>();
        for (Country c : countries) {
            com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
            if (sr != null) set.add(sr.getNameCn());
        }
        return new ArrayList<>(set);
    }

    /** 按子区域过滤国家 O(C)。参数可为中文名("东南亚")或键("SEA") */
    public List<Country> getCountriesByRegion(String region) {
        List<Country> result = new ArrayList<>();
        for (Country c : countries) {
            String key = c.getSubregion();
            if (key == null) continue;
            com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(key);
            if (sr != null && (sr.getNameCn().equals(region) || key.equals(region)))
                result.add(c);
        }
        return result;
    }

    /** 获取仪表盘统计数据 O(1) */
    public DashboardStats getDashboardStats() {
        DashboardStats s = new DashboardStats();
        if (countries == null) { s.setTotalCountries(0); return s; }
        s.setTotalCountries(countries.size());

        long coopEvts = index != null && index.statsByMonth != null
                ? index.statsByMonth.values().stream().mapToLong(ms -> ms.coopCount).sum() : 0;
        long conflictEvts = index != null && index.statsByMonth != null
                ? index.statsByMonth.values().stream().mapToLong(ms -> ms.conflictCount).sum() : 0;
        double avgCoop = index != null && index.statsByMonth != null
                ? index.statsByMonth.values().stream().mapToDouble(ms -> ms.coopIndex).average().orElse(50) : 50;
        double avgConflict = index != null && index.statsByMonth != null
                ? index.statsByMonth.values().stream().mapToDouble(ms -> ms.conflictRatio).average().orElse(0) : 0;
        double avgTone = index != null && index.statsByMonth != null
                ? index.statsByMonth.values().stream().mapToDouble(ms -> ms.avgTone).average().orElse(0) : 0;

        s.setTotalCooperationEvents((int) coopEvts);
        s.setTotalConflictEvents((int) conflictEvts);
        s.setAvgCooperationIndex(avgCoop);
        s.setAvgConflictRatio(avgConflict);
        s.setAvgTone(avgTone);
        s.setDataUpdateTime(java.time.LocalDateTime.now().toString());
        s.setTopRisks(getRiskRankings(5));
        s.setTopHotspots(getHotspots(5));
        return s;
    }

    /** 将中文区域名称转换为 Subregion 键 */
    private static String regionToKey(String regionCn) {
        if (regionCn == null) return "AF";
        switch (regionCn) {
            case "东南亚": return "SEA";
            case "中亚":   return "CA";
            case "中东":   return "ME";
            case "非洲":   return "AF";
            case "南亚":   return "SA";
            default:     return "AF";
        }
    }

    /** 获取原始事件（用于前端详情面板） */
    public List<Event> getRawEvents(String countryCode, int year, int month) {
        String key = MonthlyStats.key(countryCode, year, month);
        List<Event> events = bilateralEvents.get(key);
        return events != null ? new ArrayList<>(events) : Collections.emptyList();
    }

    // ==================== JSON 导出（辅助功能） ====================

    /**
     * 获取关联规则挖掘结果（Apriori 算法）。
     * <p>
     * 分析"当 A 国与 B 国发生 X 类事件后，后续倾向于发生 Y 类事件"的模式。
     * 默认最小支持度 0.02，最小置信度 0.5，最大项集大小 3。
     * </p>
     *
     * @return 关联规则列表（按提升度降序排列）
     */
    public List<AssociationRule> getAssociationRules() {
        return getAssociationRules(0.02, 0.5, 3);
    }

    /**
     * 获取关联规则挖掘结果，可自定义阈值。
     *
     * @param minSupport    最小支持度 (0~1)
     * @param minConfidence 最小置信度 (0~1)
     * @param maxItemsetSize 最大项集大小
     * @return 关联规则列表（按提升度降序排列）
     */
    public List<AssociationRule> getAssociationRules(double minSupport, double minConfidence, int maxItemsetSize) {
        // 收集所有原始事件
        List<Event> allEvents = new ArrayList<>();
        for (List<Event> list : bilateralEvents.values()) {
            allEvents.addAll(list);
        }
        // 如果引擎数据为空，尝试从数据库加载（如果已存在 start 时自动加载的数据，bilateralEvents 会有值）
        if (allEvents.isEmpty()) {
            return Collections.emptyList();
        }

        AprioriRuleMiner miner = new AprioriRuleMiner(minSupport, minConfidence, maxItemsetSize);
        return miner.mine(allEvents, countries);
    }

    /**
     * 导出 6 个 JSON 文件到指定目录。
     * 这是辅助功能，用于离线报告或前端开发阶段。
     */
    public void exportJson(String outputDir) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

        // 构建生成器需要的原始事件列表
        List<Event> allEvents = new ArrayList<>();
        for (List<Event> list : bilateralEvents.values()) allEvents.addAll(list);

        JsonOutputGenerator gen = new JsonOutputGenerator(countries, allEvents);
        int year = java.time.LocalDate.now().getYear();
        gen.writeAll(outputDir, year);
        System.out.println("  → JSON 已导出到: " + outputDir);
    }
}
