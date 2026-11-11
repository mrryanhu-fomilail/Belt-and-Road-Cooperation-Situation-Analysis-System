package com.yourteam.bri.model;

import java.time.LocalDate;

/**
 * 时间范围模型 - 封装起始日期和结束日期，用于时间轴筛选
 * <p>
 * 配合 TimelinePanel 使用，当用户在底部时间轴上拖动滑块选择时间范围时，
 * 系统会创建一个 TimeRange 对象并通知各面板按此范围过滤数据。
 * 也用于事件查询（QueryPanel）和趋势分析（TrendChartPanel）的日期范围设定。
 * </p>
 *
 * @see com.yourteam.bri.gui.TimelinePanel 时间轴滑块交互
 * @see com.yourteam.bri.gui.QueryPanel 事件查询的日期筛选
 */
public class TimeRange {
    /** 时间范围起始日期（包含）*/
    private LocalDate startDate;
    /** 时间范围结束日期（包含）*/
    private LocalDate endDate;

    public TimeRange() {}

    public TimeRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and Setters
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
