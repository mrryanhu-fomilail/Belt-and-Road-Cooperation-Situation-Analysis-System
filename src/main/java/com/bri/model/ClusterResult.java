package com.bri.model;

/** K-Means 聚类结果 */
public class ClusterResult {
    private String countryCode;
    private String countryName;
    private int clusterId;
    private String clusterLabel;  // "深度合作伙伴"/"稳定合作"/"存在风险"/"高度紧张"
    private double riskScore;
    private String riskLevel;     // "低风险"/"中风险"/"高风险"/"极高风险"
    private double cooperationIndex;
    private double conflictRatio;

    public ClusterResult() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String v) { this.countryName = v; }
    public int getClusterId() { return clusterId; }
    public void setClusterId(int v) { this.clusterId = v; }
    public String getClusterLabel() { return clusterLabel; }
    public void setClusterLabel(String v) { this.clusterLabel = v; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double v) { this.riskScore = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public double getCooperationIndex() { return cooperationIndex; }
    public void setCooperationIndex(double v) { this.cooperationIndex = v; }
    public double getConflictRatio() { return conflictRatio; }
    public void setConflictRatio(double v) { this.conflictRatio = v; }
}
