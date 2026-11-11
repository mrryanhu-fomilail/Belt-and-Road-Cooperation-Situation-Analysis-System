package com.yourteam.bri.model;

/**
 * 合作指数模型 - 衡量中国与一带一路沿线国家在特定月份的综合性合作水平
 * <p>
 * 合作指数（indexValue）是综合多个维度的归一化指标，范围 0~1，
 * 数值越高表示合作越紧密。综合考量以下因素：
 * <ul>
 *   <li>事件数量（eventCount）</li>
 *   <li>冲突事件占比（conflictRatio）</li>
 *   <li>平均 Goldstein 量表值（avgGoldstein）</li>
 *   <li>平均情感语调（avgTone）</li>
 *   <li>媒体关注度（mediaAttention）</li>
 * </ul>
 * </p>
 *
 * @see com.yourteam.bri.service.DataService#getCooperationRankings() 获取所有国家的排名
 * @see com.yourteam.bri.service.DataService#getBilateralTrend(String, LocalDate, LocalDate) 获取月度趋势
 */
public class CooperationIndex {
    /** 国家三字母代码 */
    private String countryCode;
    /** 国家中文名称 */
    private String countryName;
    /** 所属区域（如"东南亚""中东"等）*/
    private String region;
    /** 数据月份（格式："2024-01"）*/
    private String month;
    /** 综合合作指数（0~1），越高表示合作越紧密 */
    private double indexValue;
    /** 当月事件总数 */
    private double eventCount;
    /** 冲突事件占比（0~1）*/
    private double conflictRatio;
    /** 当月所有事件的 Goldstein 量表均值 */
    private double avgGoldstein;
    /** 当月所有事件的情感语调均值 */
    private double avgTone;
    /** 媒体关注度（0~1），反映国际媒体对该国的报道热度 */
    private double mediaAttention;

    public CooperationIndex() {}

    public CooperationIndex(String countryCode, String countryName, String region, String month,
                            double indexValue, double eventCount, double conflictRatio,
                            double avgGoldstein, double avgTone, double mediaAttention) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.region = region;
        this.month = month;
        this.indexValue = indexValue;
        this.eventCount = eventCount;
        this.conflictRatio = conflictRatio;
        this.avgGoldstein = avgGoldstein;
        this.avgTone = avgTone;
        this.mediaAttention = mediaAttention;
    }

    // Getters and Setters
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public double getIndexValue() { return indexValue; }
    public void setIndexValue(double indexValue) { this.indexValue = indexValue; }
    public double getEventCount() { return eventCount; }
    public void setEventCount(double eventCount) { this.eventCount = eventCount; }
    public double getConflictRatio() { return conflictRatio; }
    public void setConflictRatio(double conflictRatio) { this.conflictRatio = conflictRatio; }
    public double getAvgGoldstein() { return avgGoldstein; }
    public void setAvgGoldstein(double avgGoldstein) { this.avgGoldstein = avgGoldstein; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double avgTone) { this.avgTone = avgTone; }
    public double getMediaAttention() { return mediaAttention; }
    public void setMediaAttention(double mediaAttention) { this.mediaAttention = mediaAttention; }
}
