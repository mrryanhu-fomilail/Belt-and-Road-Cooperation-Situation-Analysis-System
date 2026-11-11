package com.bri.analysis;

import com.bri.model.*;
import java.util.List;
import java.util.Map;

/**
 * 分析服务接口 — 算法模块对外暴露的所有方法
 *
 * <p>由算法分析同学实现。前端/可视化模块通过本接口获取分析结果。</p>
 *
 * <h3>功能覆盖（大作业必做）</h3>
 * <ul>
 *   <li>F-12 合作指数计算</li>
 *   <li>F-13 关系趋势分析</li>
 *   <li>F-14 区域聚类分析（K-Means）</li>
 *   <li>F-15 投资风险评估</li>
 *   <li>F-16 合作热点追踪</li>
 * </ul>
 */
public interface AnalysisService {

    /** 计算合作指数。公式: Σ(Goldstein × log(1+Mentions)) / N × 10 + 5，映射到 0~100 */
    double calculateCooperationIndex(List<Event> events);

    /** 批量计算多国合作指数 */
    Map<String, Double> calculateBatchCooperationIndex(Map<String, List<Event>> eventsMap);

    /** 计算冲突比例 (0~1) */
    double calculateConflictRatio(List<Event> events);

    /** 按 CAMEO EventRootCode 分类统计 */
    Map<String, Integer> classifyByCAMEO(List<Event> events);

    /** 计算情感均值 (-10 ~ +10) */
    double calculateAverageTone(List<Event> events);

    /** 情感分类: "正面"/"负面"/"中性" */
    String classifySentiment(double avgTone);

    /** 趋势 = (当前-上一期) / 上一期 × 100% */
    double calculateTrend(double currentIndex, double previousIndex);

    /** 识别趋势拐点 */
    List<Integer> findInflectionPoints(List<Double> indexSeries);

    /** K-Means 聚类（Weka实现），k=聚类数, seed=随机种子 */
    List<ClusterResult> performClusteringWithWeka(List<ClusteringFeature> features, int k, int seed);

    /** 风险评分 = 冲突比例×40 + (100-合作指数)×0.3 + |负趋势|×20 */
    double calculateRiskScore(double conflictRatio, double coopIndex, double trend);

    /** 根据风险评分返回风险等级 */
    String getRiskLevel(double riskScore);

    /** 找出合作指数环比增长最快的 TOP N */
    List<HotspotCountry> findCooperationHotspots(Map<String, Double> currentData,
            Map<String, Double> previousData, int limit);

    /** 构建聚类特征向量 */
    ClusteringFeature buildFeature(String countryCode, List<Event> events);

    /** 生成仪表盘统计 */
    DashboardStats generateDashboardStats(List<CooperationIndex> allIndices, List<Country> countries);
}
