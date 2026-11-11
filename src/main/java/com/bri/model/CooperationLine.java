package com.bri.model;

/** 合作线路 — 两国间合作连线，对应 cooperation_lines.json */
public class CooperationLine {
    private String sourceCode;
    private String sourceName;
    private double sourceLat;
    private double sourceLon;
    private String targetCode;
    private String targetName;
    private double targetLat;
    private double targetLon;
    private double strength;       // 0~100
    private String type;           // "经贸合作"/"基础设施"/"金融合作"/"人文交流"
    private int year;
    private String status;         // "active"/"pending"
    private double goldsteinMean;
    private int eventCount;

    public CooperationLine() {}

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String v) { this.sourceCode = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { this.sourceName = v; }
    public double getSourceLat() { return sourceLat; }
    public void setSourceLat(double v) { this.sourceLat = v; }
    public double getSourceLon() { return sourceLon; }
    public void setSourceLon(double v) { this.sourceLon = v; }
    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String v) { this.targetCode = v; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }
    public double getTargetLat() { return targetLat; }
    public void setTargetLat(double v) { this.targetLat = v; }
    public double getTargetLon() { return targetLon; }
    public void setTargetLon(double v) { this.targetLon = v; }
    public double getStrength() { return strength; }
    public void setStrength(double v) { this.strength = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public int getYear() { return year; }
    public void setYear(int v) { this.year = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public double getGoldsteinMean() { return goldsteinMean; }
    public void setGoldsteinMean(double v) { this.goldsteinMean = v; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int v) { this.eventCount = v; }
}
