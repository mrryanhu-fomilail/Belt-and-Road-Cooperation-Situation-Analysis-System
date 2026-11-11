package com.bri.model;

/**
 * GDELT 事件实体 — 对应数据库 events 主表，存储单条全球事件记录。
 *
 * <p>本类为不可变值对象风格 (value object) 的 POJO：
 * 字段按 Java Bean 规范提供 getter/setter，供 DAO、CSV 映射等框架使用。
 * 各字段含义详见 GDELT 2.0 Event 数据字典及团队接口说明文档。</p>
 *
 * <h3>关键字段速查</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>GDELT 原始列(0-based)</th></tr>
 *   <tr><td>eventId</td><td>event_id</td><td>字段0 GLOBALEVENTID</td></tr>
 *   <tr><td>eventDate</td><td>event_date</td><td>字段1 SQLDATE</td></tr>
 *   <tr><td>actor1Code</td><td>actor1_code</td><td>字段7 Actor1Code</td></tr>
 *   <tr><td>actor2Code</td><td>actor2_code</td><td>字段17 Actor2Code</td></tr>
 *   <tr><td>eventRoot</td><td>event_root</td><td>字段28 EventRootCode</td></tr>
 *   <tr><td>eventCode</td><td>event_code</td><td>字段26 EventCode</td></tr>
 *   <tr><td>isCoop</td><td>is_coop</td><td>派生字段</td></tr>
 *   <tr><td>goldstein</td><td>goldstein</td><td>字段30 GoldsteinScale</td></tr>
 *   <tr><td>avgTone</td><td>avg_tone</td><td>字段34 AvgTone</td></tr>
 *   <tr><td>numMentions</td><td>num_mentions</td><td>字段31 NumMentions</td></tr>
 *   <tr><td>lat</td><td>lat</td><td>ActionGeo_Lat</td></tr>
 *   <tr><td>lon</td><td>lon</td><td>ActionGeo_Long</td></tr>
 * </table>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see EventCategory
 * @see com.bri.dao.EventDao
 */
public class Event {

    /** 全局事件 ID (GLOBALEVENTID) */
    private long eventId;

    /** 事件日期，ISO 格式 yyyy-MM-dd */
    private String eventDate;

    /** 施动国 CAMEO 三位码 */
    private String actor1Code;

    /** 受动国 CAMEO 三位码 */
    private String actor2Code;

    /** 事件大类 EventRootCode (01-20) */
    private String eventRoot;

    /** 完整事件码 EventCode */
    private String eventCode;

    /** 合作/冲突标记：1=合作、0=冲突、-1=其他，见 {@link EventCategory} */
    private int isCoop;

    /** Goldstein 分值，范围 -10 ~ +10 */
    private double goldstein;

    /** 媒体情感语调 AvgTone */
    private double avgTone;

    /** 媒体报道量 NumMentions */
    private int numMentions;

    /** 纬度（清洗后） */
    private double lat;

    /** 经度（清洗后） */
    private double lon;

    /** 新闻来源链接 (SOURCEURL) */
    private String sourceUrl;

    // ==================== 构造方法 ====================

    /** 无参构造，供框架/反序列化使用。 */
    public Event() {}
     /*
     * @param eventId     全局事件 ID
     * @param eventDate   事件日期 (yyyy-MM-dd)
     * @param actor1Code  施动国 CAMEO 码
     * @param actor2Code  受动国 CAMEO 码
     * @param eventRoot   事件大类
     * @param eventCode   完整事件码
     * @param isCoop      合作/冲突标记
     * @param goldstein   Goldstein 分值
     * @param avgTone     媒体情感语调
     * @param numMentions 媒体报道量
     * @param sourceUrl   新闻来源链接
     */
    public Event(long eventId, String eventDate, String actor1Code,
                 String actor2Code, String eventRoot, String eventCode,
                 int isCoop, double goldstein, double avgTone,
                 int numMentions, String sourceUrl) {
        this.eventId = eventId;
        this.eventDate = eventDate;
        this.actor1Code = actor1Code;
        this.actor2Code = actor2Code;
        this.eventRoot = eventRoot;
        this.eventCode = eventCode;
        this.isCoop = isCoop;
        this.goldstein = goldstein;
        this.avgTone = avgTone;
        this.numMentions = numMentions;
        this.sourceUrl = sourceUrl;
    }

    // ==================== Getters / Setters ====================

    /** @return 全局事件 ID */
    public long getEventId() { return eventId; }

    /** @param eventId 全局事件 ID */
    public void setEventId(long eventId) { this.eventId = eventId; }

    /** @return 事件日期 yyyy-MM-dd */
    public String getEventDate() { return eventDate; }

    /** @param eventDate 事件日期 yyyy-MM-dd */
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }

    /** @return 施动国 CAMEO 码 */
    public String getActor1Code() { return actor1Code; }

    /** @param actor1Code 施动国 CAMEO 码 */
    public void setActor1Code(String actor1Code) { this.actor1Code = actor1Code; }

    /** @return 受动国 CAMEO 码 */
    public String getActor2Code() { return actor2Code; }

    /** @param actor2Code 受动国 CAMEO 码 */
    public void setActor2Code(String actor2Code) { this.actor2Code = actor2Code; }

    /** @return 事件大类 EventRootCode */
    public String getEventRoot() { return eventRoot; }

    /** @param eventRoot 事件大类 EventRootCode */
    public void setEventRoot(String eventRoot) { this.eventRoot = eventRoot; }

    /** @return 完整事件码 EventCode */
    public String getEventCode() { return eventCode; }

    /** @param eventCode 完整事件码 EventCode */
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }

    /**
     * 返回合作/冲突标记。
     *
     * @return 1=合作、0=冲突、-1=其他
     * @see EventCategory#fromDbValue(int)
     */
    public int getIsCoop() { return isCoop; }

    /**
     * 设置合作/冲突标记。
     *
     * @param isCoop 1=合作、0=冲突、-1=其他
     */
    public void setIsCoop(int isCoop) { this.isCoop = isCoop; }

    /** @return Goldstein 分值 (-10 ~ +10) */
    public double getGoldstein() { return goldstein; }

    /** @param goldstein Goldstein 分值 */
    public void setGoldstein(double goldstein) { this.goldstein = goldstein; }

    /** @return 媒体情感语调 */
    public double getAvgTone() { return avgTone; }

    /** @param avgTone 媒体情感语调 */
    public void setAvgTone(double avgTone) { this.avgTone = avgTone; }

    /** @return 媒体报道量 */
    public int getNumMentions() { return numMentions; }

    /** @param numMentions 媒体报道量 */
    public void setNumMentions(int numMentions) { this.numMentions = numMentions; }

    /** @return 纬度 */
    public double getLat() { return lat; }

    /** @param lat 纬度 */
    public void setLat(double lat) { this.lat = lat; }

    /** @return 经度 */
    public double getLon() { return lon; }

    /** @param lon 经度 */
    public void setLon(double lon) { this.lon = lon; }

    /** @return 新闻来源链接 */
    public String getSourceUrl() { return sourceUrl; }

    /** @param sourceUrl 新闻来源链接 */
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为合作事件。
     *
     * @return {@code true} 如果 isCoop == 1
     */
    public boolean isCooperative() {
        return isCoop == 1;
    }

    /**
     * 判断是否为冲突事件。
     *
     * @return {@code true} 如果 isCoop == 0
     */
    public boolean isConflict() {
        return isCoop == 0;
    }

    /**
     * 返回该事件的 {@link EventCategory} 枚举分类。
     *
     * @return 合作/冲突/其他
     */
    public EventCategory getCategory() {
        return EventCategory.fromDbValue(isCoop);
    }

    /**
     * 判断地理坐标是否有效（非 (0,0) 且经纬度在合理范围内）。
     *
     * @return {@code true} 如果坐标有效
     */
    public boolean hasValidGeo() {
        return !(lat == 0.0 && lon == 0.0)
                && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return "Event{" +
                "id=" + eventId +
                ", date='" + eventDate + '\'' +
                ", a1='" + actor1Code + '\'' +
                ", a2='" + actor2Code + '\'' +
                ", root='" + eventRoot + '\'' +
                ", coop=" + isCoop +
                ", goldstein=" + goldstein +
                ", tone=" + avgTone +
                ", url='" + sourceUrl + '\'' +
                '}';
    }
}
