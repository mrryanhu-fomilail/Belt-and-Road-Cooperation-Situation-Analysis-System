package com.bri.model;

import java.util.List;

/** 仪表盘统计数据 */
public class DashboardStats {
    private int totalCountries;
    private int totalCooperationEvents;
    private int totalConflictEvents;
    private double avgCooperationIndex;
    private double avgConflictRatio;
    private double avgTone;
    private String dataUpdateTime;
    private List<RiskRanking> topRisks;
    private List<HotspotCountry> topHotspots;

    public DashboardStats() {}

    public int getTotalCountries() { return totalCountries; }
    public void setTotalCountries(int v) { this.totalCountries = v; }
    public int getTotalCooperationEvents() { return totalCooperationEvents; }
    public void setTotalCooperationEvents(int v) { this.totalCooperationEvents = v; }
    public int getTotalConflictEvents() { return totalConflictEvents; }
    public void setTotalConflictEvents(int v) { this.totalConflictEvents = v; }
    public double getAvgCooperationIndex() { return avgCooperationIndex; }
    public void setAvgCooperationIndex(double v) { this.avgCooperationIndex = v; }
    public double getAvgConflictRatio() { return avgConflictRatio; }
    public void setAvgConflictRatio(double v) { this.avgConflictRatio = v; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double v) { this.avgTone = v; }
    public String getDataUpdateTime() { return dataUpdateTime; }
    public void setDataUpdateTime(String v) { this.dataUpdateTime = v; }
    public List<RiskRanking> getTopRisks() { return topRisks; }
    public void setTopRisks(List<RiskRanking> v) { this.topRisks = v; }
    public List<HotspotCountry> getTopHotspots() { return topHotspots; }
    public void setTopHotspots(List<HotspotCountry> v) { this.topHotspots = v; }
}
