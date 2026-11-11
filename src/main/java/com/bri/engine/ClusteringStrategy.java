package com.bri.engine;

import com.bri.model.ClusterResult;
import com.bri.model.ClusteringFeature;
import java.util.List;

/**
 * 聚类策略接口 — 开闭原则的体现。
 *
 * <p>当前用 Weka K-Means，将来可替换为：
 * <ul>
 *   <li>{@code DBSCANClustering} — 密度聚类，不需要预设 K</li>
 *   <li>{@code HierarchicalClustering} — 层次聚类</li>
 *   <li>{@code XMeansClustering} — 自动确定 K 值</li>
 * </ul>
 * 替换时仅需实现本接口，{@link BriAnalysisEngine} 一行代码不改。</p>
 */
public interface ClusteringStrategy {

    /**
     * 执行聚类分析。
     *
     * @param features 各国特征向量
     * @param k        目标聚类数（某些算法可能忽略此参数）
     * @return 聚类结果列表
     */
    List<ClusterResult> cluster(List<ClusteringFeature> features, int k);
}
