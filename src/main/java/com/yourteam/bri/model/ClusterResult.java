package com.yourteam.bri.model;

/**
 * K-Means 聚类分析结果模型 - 将一带一路沿线国家按合作特征分组并评估投资风险
 * <p>
 * 基于以下特征对沿线国家进行聚类分析：
 * <ul>
 *   <li>合作指数（合作密度）</li>
 *   <li>冲突事件频率</li>
 *   <li>Goldstein 均值</li>
 *   <li>情感语调均值</li>
 *   <li>媒体关注度</li>
 * </ul>
 * </p>
 * <p>
 * 聚类分为四大类，颜色编码在 UI 中区分展示：
 * <ul>
 *   <li>Cluster 0: "深度合作伙伴"（?? 低风险）</li>
 *   <li>Cluster 1: "稳定合作"（?? 低风险）</li>
 *   <li>Cluster 2: "存在风险"（?? 中等风险）</li>
 *   <li>Cluster 3: "高度紧张"（?? 高风险）</li>
 * </ul>
 * </p>
 *
 * @see com.yourteam.bri.gui.ClusteringPanel 聚类结果的可视化展示
 * @see com.yourteam.bri.service.DataService#getClusterResults() 聚类数据生成
 */
public class ClusterResult {
    /** 国家三字母代码 */
    private String countryCode;
    /** 国家中文名称 */
    private String countryName;
    /** 聚类编号（0~3，对应四种分类）*/
    private int clusterId;
    /** 聚类标签（"深度合作伙伴" / "稳定合作" / "存在风险" / "高度紧张"）*/
    private String clusterLabel;
    /** 风险等级（"低" / "中" / "高" / "极高"）*/
    private String riskLevel;
    /** 风险评分（0~100，越高表示风险越大）*/
    private double riskScore;

    public ClusterResult() {}

    public ClusterResult(String countryCode, String countryName, int clusterId,
                         String clusterLabel, String riskLevel, double riskScore) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.clusterId = clusterId;
        this.clusterLabel = clusterLabel;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
    }

    // Getters and Setters
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public int getClusterId() { return clusterId; }
    public void setClusterId(int clusterId) { this.clusterId = clusterId; }
    public String getClusterLabel() { return clusterLabel; }
    public void setClusterLabel(String clusterLabel) { this.clusterLabel = clusterLabel; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
}
