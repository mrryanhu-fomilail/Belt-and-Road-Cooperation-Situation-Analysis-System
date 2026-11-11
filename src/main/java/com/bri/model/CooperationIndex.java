package com.bri.model;

/**
 * 合作指数 — 按月记录某国的合作指数计算结果
 * 对应输出文件 cooperation_index.json
 */
public class CooperationIndex {
    private String countryCode;
    private String countryName;
    private int year;
    private int month;
    private double index;          // 合作指数 0~100
    private double conflictRatio;
    private double avgTone;
    private int eventCount;
    private double trend;          // 环比变化率(%)
    private String trendDirection; // "up"/"down"/"stable"

    public CooperationIndex() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String v) { this.countryName = v; }
    public int getYear() { return year; }
    public void setYear(int v) { this.year = v; }
    public int getMonth() { return month; }
    public void setMonth(int v) { this.month = v; }
    public double getIndex() { return index; }
    public void setIndex(double v) { this.index = v; }
    public double getConflictRatio() { return conflictRatio; }
    public void setConflictRatio(double v) { this.conflictRatio = v; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double v) { this.avgTone = v; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int v) { this.eventCount = v; }
    public double getTrend() { return trend; }
    public void setTrend(double v) { this.trend = v; }
    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String v) { this.trendDirection = v; }
}
