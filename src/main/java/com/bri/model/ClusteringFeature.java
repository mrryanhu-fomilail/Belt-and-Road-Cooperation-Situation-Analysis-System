package com.bri.model;

/** 聚类特征向量（供 K-Means 使用） */
public class ClusteringFeature {
    private String countryCode;
    private String dateRange;
    private double cooperationIndex;
    private double conflictRatio;
    private double avgTone;
    private double trendValue;
    private double normalizedCoopIndex;
    private double normalizedConflictRatio;
    private double normalizedTone;
    private double normalizedTrend;

    public ClusteringFeature() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getDateRange() { return dateRange; }
    public void setDateRange(String v) { this.dateRange = v; }
    public double getCooperationIndex() { return cooperationIndex; }
    public void setCooperationIndex(double v) { this.cooperationIndex = v; }
    public double getConflictRatio() { return conflictRatio; }
    public void setConflictRatio(double v) { this.conflictRatio = v; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double v) { this.avgTone = v; }
    public double getTrendValue() { return trendValue; }
    public void setTrendValue(double v) { this.trendValue = v; }
    public double getNormalizedCoopIndex() { return normalizedCoopIndex; }
    public void setNormalizedCoopIndex(double v) { this.normalizedCoopIndex = v; }
    public double getNormalizedConflictRatio() { return normalizedConflictRatio; }
    public void setNormalizedConflictRatio(double v) { this.normalizedConflictRatio = v; }
    public double getNormalizedTone() { return normalizedTone; }
    public void setNormalizedTone(double v) { this.normalizedTone = v; }
    public double getNormalizedTrend() { return normalizedTrend; }
    public void setNormalizedTrend(double v) { this.normalizedTrend = v; }
}
