package com.bri.model;

/** 风险排名项 */
public class RiskRanking {
    private int rank;
    private String countryCode;
    private String countryName;
    private double riskScore;
    private String riskLevel;
    private String trend;

    public RiskRanking() {}

    public int getRank() { return rank; }
    public void setRank(int v) { this.rank = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String v) { this.countryName = v; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double v) { this.riskScore = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public String getTrend() { return trend; }
    public void setTrend(String v) { this.trend = v; }
}
