package com.bri.model;

/**
 * 月度趋势数据模型 — 按月统计的双边关系指标。
 * <p>用于趋势分析面板，包含某月的事件数量、Goldstein 均值、情感语调均值。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 */
public class MonthlyTrend {

    /** 月份，格式 yyyy-MM */
    private String month;

    /** 事件数量 */
    private int eventCount;

    /** Goldstein 均值 */
    private double avgGoldstein;

    /** 情感语调均值 */
    private double avgTone;

    /** 合作事件占比（0~1） */
    private double coopRatio;

    public MonthlyTrend() {}

    public MonthlyTrend(String month, int eventCount, double avgGoldstein, double avgTone, double coopRatio) {
        this.month = month;
        this.eventCount = eventCount;
        this.avgGoldstein = avgGoldstein;
        this.avgTone = avgTone;
        this.coopRatio = coopRatio;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public double getAvgGoldstein() { return avgGoldstein; }
    public void setAvgGoldstein(double avgGoldstein) { this.avgGoldstein = avgGoldstein; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double avgTone) { this.avgTone = avgTone; }
    public double getCoopRatio() { return coopRatio; }
    public void setCoopRatio(double coopRatio) { this.coopRatio = coopRatio; }
}
