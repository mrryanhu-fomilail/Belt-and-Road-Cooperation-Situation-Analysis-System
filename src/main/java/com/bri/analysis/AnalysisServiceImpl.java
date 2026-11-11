package com.bri.analysis;

import com.bri.model.*;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 分析服务实现 — 所有算法计算逻辑（无状态，线程安全）
 *
 * <h3>核心公式</h3>
 * <ul>
 *   <li>合作指数 = Σ(Goldstein × log(1+Mentions)) / N × 10 + 5 → 映射到0~100</li>
 *   <li>冲突比例 = 冲突事件数 / 总事件数</li>
 *   <li>风险评分 = 冲突比×40 + (100-合作指数)×0.3 + |负趋势|×20</li>
 *   <li>趋势 = (当前-上期) / 上期 × 100%</li>
 * </ul>
 */
public class AnalysisServiceImpl implements AnalysisService {

    @Override
    public double calculateCooperationIndex(List<Event> events) {
        if (events == null || events.isEmpty()) return 50.0;
        double sum = 0.0;
        int count = 0;
        for (Event e : events) {
            sum += e.getGoldstein() * Math.log(1.0 + Math.max(0, e.getNumMentions()));
            count++;
        }
        double raw = (sum / count) * 10.0 + 5.0;
        return clamp(raw * 5.0, 0.0, 100.0);
    }

    @Override
    public Map<String, Double> calculateBatchCooperationIndex(Map<String, List<Event>> eventsMap) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Event>> e : eventsMap.entrySet())
            result.put(e.getKey(), calculateCooperationIndex(e.getValue()));
        return result;
    }

    @Override
    public double calculateConflictRatio(List<Event> events) {
        if (events == null || events.isEmpty()) return 0.0;
        long cnt = events.stream().filter(Event::isConflict).count();
        return (double) cnt / events.size();
    }

    @Override
    public Map<String, Integer> classifyByCAMEO(List<Event> events) {
        Map<String, Integer> result = new TreeMap<>();
        if (events == null) return result;
        for (Event e : events) {
            String root = e.getEventRoot();
            if (root != null && !root.isEmpty()) result.merge(root, 1, Integer::sum);
        }
        return result;
    }

    @Override
    public double calculateAverageTone(List<Event> events) {
        if (events == null || events.isEmpty()) return 0.0;
        return events.stream().mapToDouble(Event::getAvgTone).average().orElse(0.0);
    }

    @Override
    public String classifySentiment(double avgTone) {
        if (avgTone > 1.0) return "正面";
        if (avgTone < -1.0) return "负面";
        return "中性";
    }

    @Override
    public double calculateTrend(double current, double previous) {
        if (Math.abs(previous) < 1e-9) return current > 0 ? 100.0 : 0.0;
        return (current - previous) / previous * 100.0;
    }

    @Override
    public List<Integer> findInflectionPoints(List<Double> series) {
        List<Integer> pts = new ArrayList<>();
        if (series == null || series.size() < 3) return pts;
        for (int i = 1; i < series.size() - 1; i++) {
            double a = series.get(i) - series.get(i - 1);
            double b = series.get(i + 1) - series.get(i);
            if (a * b < 0) pts.add(i);
        }
        return pts;
    }

    // ---- K-Means ----
    @Override
    public List<ClusterResult> performClusteringWithWeka(List<ClusteringFeature> features, int k, int seed) {
        if (features == null || features.isEmpty()) return Collections.emptyList();
        normalizeFeatures(features);
        try {
            ArrayList<Attribute> attrs = new ArrayList<>();
            attrs.add(new Attribute("coop"));
            attrs.add(new Attribute("conflict"));
            attrs.add(new Attribute("tone"));
            attrs.add(new Attribute("trend"));
            Instances ds = new Instances("bri", attrs, features.size());
            for (ClusteringFeature f : features)
                ds.add(new DenseInstance(1.0, new double[]{
                    f.getNormalizedCoopIndex(), f.getNormalizedConflictRatio(),
                    f.getNormalizedTone(), f.getNormalizedTrend()}));

            SimpleKMeans km = new SimpleKMeans();
            km.setNumClusters(k); km.setSeed(seed);
            km.setPreserveInstancesOrder(true);
            km.buildClusterer(ds);

            int[] asgn = km.getAssignments();
            int[] sorted = sortByCoop(km.getClusterCentroids());
            String[] labels = new String[k];
            for (int r = 0; r < k; r++) {
                switch (r) {
                    case 0: labels[sorted[r]] = "深度合作伙伴"; break;
                    case 1: labels[sorted[r]] = "稳定合作"; break;
                    case 2: labels[sorted[r]] = "存在风险"; break;
                    case 3: labels[sorted[r]] = "高度紧张"; break;
                }
            }

            List<ClusterResult> res = new ArrayList<>();
            for (int i = 0; i < features.size(); i++) {
                ClusteringFeature f = features.get(i);
                ClusterResult cr = new ClusterResult();
                cr.setCountryCode(f.getCountryCode());
                cr.setClusterId(asgn[i]);
                cr.setClusterLabel(labels[asgn[i]]);
                cr.setCooperationIndex(f.getCooperationIndex());
                cr.setConflictRatio(f.getConflictRatio());
                double risk = calculateRiskScore(f.getConflictRatio(), f.getCooperationIndex(), f.getTrendValue());
                cr.setRiskScore(risk);
                cr.setRiskLevel(getRiskLevel(risk));
                res.add(cr);
            }
            return res;
        } catch (Exception e) {
            throw new RuntimeException("K-Means 聚类失败", e);
        }
    }

    private int[] sortByCoop(Instances centroids) {
        int k = centroids.size();
        double[][] p = new double[k][2];
        for (int i = 0; i < k; i++) { p[i][0] = i; p[i][1] = centroids.get(i).value(0); }
        Arrays.sort(p, (a, b) -> Double.compare(b[1], a[1]));
        int[] r = new int[k];
        for (int i = 0; i < k; i++) r[i] = (int) p[i][0];
        return r;
    }

    private void normalizeFeatures(List<ClusteringFeature> list) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE};
        for (ClusteringFeature f : list) {
            min[0] = Math.min(min[0], f.getCooperationIndex()); max[0] = Math.max(max[0], f.getCooperationIndex());
            min[1] = Math.min(min[1], f.getConflictRatio());     max[1] = Math.max(max[1], f.getConflictRatio());
            min[2] = Math.min(min[2], f.getAvgTone());           max[2] = Math.max(max[2], f.getAvgTone());
            min[3] = Math.min(min[3], f.getTrendValue());        max[3] = Math.max(max[3], f.getTrendValue());
        }
        for (ClusteringFeature f : list) {
            f.setNormalizedCoopIndex(norm(f.getCooperationIndex(), min[0], max[0]));
            f.setNormalizedConflictRatio(norm(f.getConflictRatio(), min[1], max[1]));
            f.setNormalizedTone(norm(f.getAvgTone(), min[2], max[2]));
            f.setNormalizedTrend(norm(f.getTrendValue(), min[3], max[3]));
        }
    }

    private double norm(double v, double min, double max) {
        return Math.abs(max - min) < 1e-9 ? 0.5 : (v - min) / (max - min);
    }

    // ---- 风险 ----
    @Override
    public double calculateRiskScore(double conflictRatio, double coopIndex, double trend) {
        double penalty = trend < 0 ? Math.min(Math.abs(trend), 100.0) : 0;
        return conflictRatio * 40.0 + (100.0 - coopIndex) * 0.3 + penalty * 0.2;
    }

    @Override
    public String getRiskLevel(double score) {
        if (score < 30) return "低风险";
        if (score < 50) return "中风险";
        if (score < 70) return "高风险";
        return "极高风险";
    }

    // ---- 热点 ----
    @Override
    public List<HotspotCountry> findCooperationHotspots(Map<String, Double> cur, Map<String, Double> prev, int limit) {
        List<HotspotCountry> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : cur.entrySet()) {
            String code = e.getKey();
            double c = e.getValue();
            double p = prev.getOrDefault(code, c);
            HotspotCountry h = new HotspotCountry();
            h.setCountryCode(code);
            h.setGrowthRate(calculateTrend(c, p));
            h.setCurrentIndex(c);
            h.setPreviousIndex(p);
            list.add(h);
        }
        list.sort((a, b) -> Double.compare(b.getGrowthRate(), a.getGrowthRate()));
        if (limit > 0 && list.size() > limit) list = list.subList(0, limit);
        return list;
    }

    // ---- 特征 ----
    @Override
    public ClusteringFeature buildFeature(String countryCode, List<Event> events) {
        ClusteringFeature f = new ClusteringFeature();
        f.setCountryCode(countryCode);
        f.setCooperationIndex(calculateCooperationIndex(events));
        f.setConflictRatio(calculateConflictRatio(events));
        f.setAvgTone(calculateAverageTone(events));
        f.setTrendValue(0);
        return f;
    }

    // ---- 仪表盘 ----
    @Override
    public DashboardStats generateDashboardStats(List<CooperationIndex> indices, List<Country> countries) {
        DashboardStats s = new DashboardStats();
        s.setTotalCountries(countries.size());
        double sumIdx = 0, sumConf = 0, sumTone = 0;
        for (CooperationIndex ci : indices) {
            sumIdx += ci.getIndex(); sumConf += ci.getConflictRatio(); sumTone += ci.getAvgTone();
        }
        int sz = Math.max(1, indices.size());
        s.setAvgCooperationIndex(sumIdx / sz);
        s.setAvgConflictRatio(sumConf / sz);
        s.setAvgTone(sumTone / sz);
        s.setDataUpdateTime(LocalDateTime.now().toString());
        return s;
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- 五维度拆分（供 JsonOutputGenerator 使用） ----
    public static Map<String, Double> splitToFiveDimensions(double coopIndex, Map<String, Integer> cameoDist) {
        Map<String, Double> dims = new LinkedHashMap<>();
        dims.put("economy", 0.0);
        dims.put("infrastructure", 0.0);
        dims.put("finance", 0.0);
        dims.put("culture", 0.0);
        dims.put("policy", 0.0);
        int total = cameoDist.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return dims;

        double[] w = new double[5];
        for (Map.Entry<String, Integer> e : cameoDist.entrySet()) {
            int root = Integer.parseInt(e.getKey());
            double ratio = (double) e.getValue() / total;
            if (root == 2 || root == 3 || root == 6) { w[4] += ratio; }
            else if (root == 4 || root == 5) { w[0] += ratio * 0.5; w[1] += ratio * 0.5; }
            else if (root == 7 || root == 8) { w[2] += ratio * 0.6; w[0] += ratio * 0.4; }
            else if (root == 1) { w[3] += ratio * 0.7; w[4] += ratio * 0.3; }
            else { for (int i = 0; i < 5; i++) w[i] += ratio * 0.2; }
        }
        dims.put("economy", round(coopIndex * w[0]));
        dims.put("infrastructure", round(coopIndex * w[1]));
        dims.put("finance", round(coopIndex * w[2]));
        dims.put("culture", round(coopIndex * w[3]));
        dims.put("policy", round(coopIndex * w[4]));
        return dims;
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }
}
