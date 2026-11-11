package com.bri.model;

/** 五维度评分（雷达图），对应 scores.json */
public class ScoreItem {
    private String countryCode;
    private int year;
    private double economy;
    private double infrastructure;
    private double finance;
    private double culture;
    private double policy;

    public ScoreItem() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public int getYear() { return year; }
    public void setYear(int v) { this.year = v; }
    public double getEconomy() { return economy; }
    public void setEconomy(double v) { this.economy = v; }
    public double getInfrastructure() { return infrastructure; }
    public void setInfrastructure(double v) { this.infrastructure = v; }
    public double getFinance() { return finance; }
    public void setFinance(double v) { this.finance = v; }
    public double getCulture() { return culture; }
    public void setCulture(double v) { this.culture = v; }
    public double getPolicy() { return policy; }
    public void setPolicy(double v) { this.policy = v; }
}
