package com.bri.engine;

import com.bri.model.*;

import java.util.*;

/**
 * 预计算索引 — 空间换时间的核心数据仓库。
 *
 * <p>所有月度统计 + 聚类结果 + 风险排名在 load 阶段一次性算完存入本对象。
 * 之后所有查询方法都是 Map.get() 或数组访问，O(1) 或 O(log K) 时间。</p>
 *
 * <h3>内存模型</h3>
 * <pre>
 *   statsByMonth: "PAK#2026-06" → MonthlyStats{coop=72.5, conflict=0.12, ...}
 *   clusters:     [ {IRN→深度合作伙伴}, {PAK→稳定合作}, ... ]
 *   rankings:     [ {ISR → 高风险 #1}, {UKR → 高风险 #2}, ... ]
 *   hotspots:     [ {SGP → +35%}, {VNM → +28%}, ... ]
 * </pre>
 */
class PrecomputedIndex {

    /** "PAK#2026-06" → MonthlyStats */
    final Map<String, MonthlyStats> statsByMonth;

    /** 聚类结果 */
    final List<ClusterResult> clusters;

    /** 风险排名（按 riskScore 降序） */
    final List<RiskRanking> rankings;

    /** 热点国家（按增长率降序，TOP 10） */
    final List<HotspotCountry> hotspots;

    /** 国家列表 */
    final List<Country> countries;

    /** 计算器 */
    final IndexCalculator calculator;

    /** 聚类策略 */
    final ClusteringStrategy clusteringStrategy;

    /** 数据年份范围 */
    final int minYear;
    final int maxYear;

    private boolean built = false;

    PrecomputedIndex(List<Country> countries, IndexCalculator calculator,
                     ClusteringStrategy clusteringStrategy) {
        this.countries = countries;
        this.calculator = calculator;
        this.clusteringStrategy = clusteringStrategy;
        this.statsByMonth = new HashMap<>(4096);
        this.clusters = new ArrayList<>();
        this.rankings = new ArrayList<>();
        this.hotspots = new ArrayList<>();
        this.minYear = 9999;
        this.maxYear = 0;
    }

    /** 批量写入月度统计 */
    void putAllStats(Map<String, MonthlyStats> stats) {
        statsByMonth.putAll(stats);
    }

    /** 构建派生索引：聚类 + 排名 + 热点 */
    void buildDerived() {
        if (built) return;
        built = true;

        // ---- 聚类 ----
        List<ClusteringFeature> feats = new ArrayList<>();
        for (Country c : countries) {
            // 拿该国所有月份的 mean
            List<MonthlyStats> allMonths = new ArrayList<>();
            for (Map.Entry<String, MonthlyStats> e : statsByMonth.entrySet()) {
                if (e.getKey().startsWith(c.getCameoCode() + "#"))
                    allMonths.add(e.getValue());
            }
            if (allMonths.isEmpty()) continue;

            double meanCoop = allMonths.stream().mapToDouble(s -> s.coopIndex).average().orElse(50);
            double meanConf = allMonths.stream().mapToDouble(s -> s.conflictRatio).average().orElse(0);
            double meanTone = allMonths.stream().mapToDouble(s -> s.avgTone).average().orElse(0);

            ClusteringFeature f = new ClusteringFeature();
            f.setCountryCode(c.getCameoCode());
            f.setCooperationIndex(meanCoop);
            f.setConflictRatio(meanConf);
            f.setAvgTone(meanTone);
            f.setTrendValue(0);
            feats.add(f);
        }
        if (feats.size() >= 4)
            clusters.addAll(clusteringStrategy.cluster(feats, 4));

        // ---- 风险排名 ----
        rankings.clear();
        int rank = 1;
        for (ClusterResult cr : clusters) {
            RiskRanking r = new RiskRanking();
            r.setRank(rank++);
            r.setCountryCode(cr.getCountryCode());
            r.setRiskScore(cr.getRiskScore());
            r.setRiskLevel(cr.getRiskLevel());
            r.setTrend("stable");
            rankings.add(r);
        }
        rankings.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));

        // ---- 热点（环比增长率） ----
        hotspots.clear();
        Map<String, Double> thisYear = new HashMap<>();
        Map<String, Double> lastYear = new HashMap<>();
        int maxY = detectMaxYear();
        for (Map.Entry<String, MonthlyStats> e : statsByMonth.entrySet()) {
            String[] parts = e.getKey().split("#");
            String code = parts[0];
            int year = Integer.parseInt(parts[1].substring(0, 4));
            if (year == maxY)
                thisYear.merge(code, e.getValue().coopIndex, (a, b) -> (a + b) / 2);
            if (year == maxY - 1)
                lastYear.merge(code, e.getValue().coopIndex, (a, b) -> (a + b) / 2);
        }
        for (Map.Entry<String, Double> e : thisYear.entrySet()) {
            String code = e.getKey();
            double cur = e.getValue();
            double prev = lastYear.getOrDefault(code, cur);
            HotspotCountry h = new HotspotCountry();
            h.setCountryCode(code);
            h.setGrowthRate(RiskAssessor.trend(cur, prev));
            h.setCurrentIndex(cur);
            h.setPreviousIndex(prev);
            hotspots.add(h);
        }
        hotspots.sort((a, b) -> Double.compare(b.getGrowthRate(), a.getGrowthRate()));
        if (hotspots.size() > 10) hotspots.subList(10, hotspots.size()).clear();
    }

    private int detectMaxYear() {
        int max = 0;
        for (String key : statsByMonth.keySet()) {
            String[] parts = key.split("#");
            int y = Integer.parseInt(parts[1].substring(0, 4));
            if (y > max) max = y;
        }
        return max;
    }
}
