package com.bri.model;

/** 热点合作国家（环比增长最快） */
public class HotspotCountry {
    private String countryCode;
    private String countryName;
    private double growthRate;      // 环比增长率(%)
    private double currentIndex;
    private double previousIndex;

    public HotspotCountry() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String v) { this.countryName = v; }
    public double getGrowthRate() { return growthRate; }
    public void setGrowthRate(double v) { this.growthRate = v; }
    public double getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(double v) { this.currentIndex = v; }
    public double getPreviousIndex() { return previousIndex; }
    public void setPreviousIndex(double v) { this.previousIndex = v; }
}
