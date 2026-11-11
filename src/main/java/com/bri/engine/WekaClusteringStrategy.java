package com.bri.engine;

import com.bri.model.ClusterResult;
import com.bri.model.ClusteringFeature;
import com.bri.model.RiskRanking;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.*;

/**
 * Weka K-Means 聚类实现。
 */
public class WekaClusteringStrategy implements ClusteringStrategy {

    private final int seed;

    public WekaClusteringStrategy(int seed) {
        this.seed = seed;
    }

    @Override
    public List<ClusterResult> cluster(List<ClusteringFeature> features, int k) {
        if (features == null || features.isEmpty()) return Collections.emptyList();
        normalize(features);
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
            km.setNumClusters(k);
            km.setSeed(seed);
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
                    default: labels[sorted[r]] = "高度紧张"; break;
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
                double risk = RiskAssessor.assess(f.getConflictRatio(), f.getCooperationIndex(), f.getTrendValue());
                cr.setRiskScore(risk);
                cr.setRiskLevel(RiskAssessor.level(risk));
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

    /** Min-Max 归一化到 [0,1] */
    void normalize(List<ClusteringFeature> list) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (ClusteringFeature f : list) {
            min[0] = Math.min(min[0], f.getCooperationIndex()); max[0] = Math.max(max[0], f.getCooperationIndex());
            min[1] = Math.min(min[1], f.getConflictRatio()); max[1] = Math.max(max[1], f.getConflictRatio());
            min[2] = Math.min(min[2], f.getAvgTone());       max[2] = Math.max(max[2], f.getAvgTone());
            min[3] = Math.min(min[3], f.getTrendValue());    max[3] = Math.max(max[3], f.getTrendValue());
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
}
