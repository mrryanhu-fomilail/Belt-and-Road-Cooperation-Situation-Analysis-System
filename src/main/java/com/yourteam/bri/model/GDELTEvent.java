package com.yourteam.bri.model;

import java.time.LocalDate;

/**
 * GDELT 事件数据模型 - 全球事件、语言与语调数据库（GDELT 2.0）的单条事件记录
 * <p>
 * GDELT 是全球最大的开放事件数据库。本模型封装与一带一路合作分析相关的核心字段，
 * 包含事件参与方、CAMEO 编码、Goldstein 量表、情感语调、地理位置等信息。
 * </p>
 * <ul>
 *   <li>Goldstein 量表（-10~10）：正值为合作事件，负值为冲突事件</li>
 *   <li>avgTone（-100~100）：媒体情感语调，正值=正面报道</li>
 *   <li>CAMEO 编码：标准冲突与调解事件观察编码，支持按大类分类</li>
 * </ul>
 *
 * @see com.yourteam.bri.service.DataService 提供模拟数据源
 */
public class GDELTEvent {
    /** 事件发生日期 */
    private LocalDate date;
    /** 参与方1 的三字母国家代码（行动发起方/主动方）*/
    private String actor1Code;
    /** 参与方2 的三字母国家代码（行动目标方/被动方）*/
    private String actor2Code;
    /** CAMEO 事件编码（如 "041"=协商 "081"=威胁 "101"=军事冲突）*/
    private String eventCode;
    /** Goldstein 量表值 (-10~10)，正数=合作性事件，负数=冲突性事件 */
    private double goldsteinScale;
    /** 媒体引用次数，反映事件的全球曝光度和重要性 */
    private int numMentions;
    /** 平均情感语调 (-100~100)，正值=正面报道倾向，负值=负面报道倾向 */
    private double avgTone;
    /** 事件发生地纬度 */
    private double latitude;
    /** 事件发生地经度 */
    private double longitude;
    /** 事件大类名称（如"合作事件""冲突事件""军事冲突"），由 getEventCategoryName() 推断 */
    private String eventCategoryName;

    /** 新闻来源 URL */
    private String sourceUrl;

    /** QuadClass 分类 (1=口头合作, 2=物质合作, 3=口头冲突, 4=物质冲突, null=未知) */
    private Integer quadClass;

    public GDELTEvent() {}

    public GDELTEvent(LocalDate date, String actor1Code, String actor2Code,
                      String eventCode, double goldsteinScale, int numMentions, double avgTone) {
        this.date = date;
        this.actor1Code = actor1Code;
        this.actor2Code = actor2Code;
        this.eventCode = eventCode;
        this.goldsteinScale = goldsteinScale;
        this.numMentions = numMentions;
        this.avgTone = avgTone;
    }

    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getActor1Code() { return actor1Code; }
    public void setActor1Code(String actor1Code) { this.actor1Code = actor1Code; }
    public String getActor2Code() { return actor2Code; }
    public void setActor2Code(String actor2Code) { this.actor2Code = actor2Code; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public double getGoldsteinScale() { return goldsteinScale; }
    public void setGoldsteinScale(double goldsteinScale) { this.goldsteinScale = goldsteinScale; }
    public int getNumMentions() { return numMentions; }
    public void setNumMentions(int numMentions) { this.numMentions = numMentions; }
    public double getAvgTone() { return avgTone; }
    public void setAvgTone(double avgTone) { this.avgTone = avgTone; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    /**
     * 判断是否为合作事件（基于 CAMEO 编码体系）
     * <p>
     * CAMEO Root Code 04~06 对应国际合作行为：
     * <ul>
     *   <li>04: 协商（Consult）</li>
     *   <li>05: 外交合作（Engage in diplomatic cooperation）</li>
     *   <li>06: 援助（Engage in material cooperation）</li>
     * </ul>
     * </p>
     *
     * @return 如果事件根编码属于 CAMEO 04~06 范围则返回 true
     */
    public boolean isCooperationEvent() {
        if (eventCode == null || eventCode.length() < 2) return false;
        try {
            int code = Integer.parseInt(eventCode.substring(0, 2));
            return code >= 4 && code <= 6;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断是否为冲突事件（基于 CAMEO 编码体系）
     * <p>
     * CAMEO Root Code 08~14 对应冲突与对抗行为：
     * <ul>
     *   <li>08: 威胁（Threaten）</li>
     *   <li>09: 抗议（Protest）</li>
     *   <li>10: 军事示威（Demonstrate military force）</li>
     *   <li>11: 军事袭击（Armed conflict / Attack）</li>
     *   <li>12: 军事行动（Conduct military operations）</li>
     *   <li>13~14: 战争行为（Mass violence / Engage in unconventional mass violence）</li>
     * </ul>
     * </p>
     *
     * @return 如果事件根编码属于 CAMEO 08~14 范围则返回 true
     */
    public boolean isConflictEvent() {
        if (eventCode == null || eventCode.length() < 2) return false;
        try {
            int code = Integer.parseInt(eventCode.substring(0, 2));
            return code >= 8 && code <= 14;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Integer getQuadClass() { return quadClass; }

    public void setQuadClass(Integer quadClass) { this.quadClass = quadClass; }

    /**
     * 获取 QuadClass 显示文本
     * @return 如 "口头合作"、"物质合作"、null 时返回 "-"
     */
    public String getQuadClassDisplay() {
        if (quadClass == null) return "-";
        switch (quadClass) {
            case 1: return "口头合作";
            case 2: return "物质合作";
            case 3: return "口头冲突";
            case 4: return "物质冲突";
            default: return "-";
        }
    }

    /**
     * 根据 CAMEO Root Code 获取正式事件分类名称
     * <p>
     * 映射规则（CAMEO 官方 EventRootCode 分类）：
     * </p>
     * <ul>
     *   <li>01: 公开发言/声明（Make statement / Comment）</li>
     *   <li>02: 呼吁/呼吁他方行动（Appeal / Request）</li>
     *   <li>03: 表达合作意向（Express intent to cooperate）</li>
     *   <li>04: 协商（Consult / Discuss）</li>
     *   <li>05: 外交合作（Engage in diplomatic cooperation）</li>
     *   <li>06: 援助合作（Engage in material cooperation）</li>
     *   <li>07: 提供援助（Provide aid）</li>
     *   <li>08: 威胁（Threaten）</li>
     *   <li>09: 抗议/外交对抗（Protest / Diplomatic confrontation）</li>
     *   <li>10: 军事示威（Demonstrate military force）</li>
     *   <li>11: 军事袭击（Armed conflict / Attack）</li>
     *   <li>12: 军事行动（Conduct military operations）</li>
     *   <li>13: 大规模暴力（Mass violence）</li>
     *   <li>14: 非常规暴力（Unconventional mass violence）</li>
     *   <li>15~17: 其他 / 自定义行为</li>
     *   <li>18~20: 其他</li>
     * </ul>
     *
     * @return 事件大类的正式中文名称，映射为五类：公开发言 / 合作事件 / 冲突事件 / 军事冲突 / 其他
     */
    public String getEventCategoryName() {
        if (eventCode == null || eventCode.length() < 2) return "未知";
        try {
            int code = Integer.parseInt(eventCode.substring(0, 2));
            if (code >= 1 && code <= 3) return "公开发言";
            if (code >= 4 && code <= 6) return "合作事件";
            if (code == 7) return "其他";
            if (code >= 8 && code <= 9) return "冲突事件";
            if (code >= 10 && code <= 14) return "军事冲突";
            return "其他";
        } catch (NumberFormatException e) {
            return "未知";
        }
    }

    public void setEventCategoryName(String eventCategoryName) {
        this.eventCategoryName = eventCategoryName;
    }

    /** @return 新闻来源 URL */
    public String getSourceUrl() { return sourceUrl; }

    /** @param sourceUrl 新闻来源 URL */
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
}

