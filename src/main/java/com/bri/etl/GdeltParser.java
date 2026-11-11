package com.bri.etl;

import com.bri.model.Event;
import com.bri.model.EventCategory;

/**
 * GDELT 2.0 TSV 行解析器 — 将单行 TSV 数据解析为 {@link Event} 实体对象。
 *
 * <p>负责列定位、类型转换、逐行清洗，并计数各类跳过原因。
 * 线程安全：纯函数，无共享可变状态。</p>
 *
 * <h3>GDELT 2.0 列映射（0-based）</h3>
 * <table>
 *   <tr><th>字段</th><th>列索引</th><th>GDELT 名称</th></tr>
 *   <tr><td>eventId</td><td>0</td><td>GLOBALEVENTID</td></tr>
 *   <tr><td>eventDate</td><td>1</td><td>SQLDATE (yyyyMMdd → yyyy-MM-dd)</td></tr>
 *   <tr><td>actor1Code</td><td>7</td><td>Actor1CountryCode</td></tr>
 *   <tr><td>actor2Code</td><td>17</td><td>Actor2CountryCode</td></tr>
 *   <tr><td>eventCode</td><td>26</td><td>EventCode</td></tr>
 *   <tr><td>eventRoot</td><td>28</td><td>EventRootCode</td></tr>
 *   <tr><td>goldstein</td><td>30</td><td>GoldsteinScale</td></tr>
 *   <tr><td>numMentions</td><td>31</td><td>NumMentions</td></tr>
 *   <tr><td>avgTone</td><td>34</td><td>AvgTone</td></tr>
 *   <tr><td>lat</td><td>53</td><td>ActionGeo_Lat</td></tr>
 *   <tr><td>lon</td><td>54</td><td>ActionGeo_Long</td></tr>
 *   <tr><td>sourceUrl</td><td>60</td><td>SOURCEURL</td></tr>
 * </table>
 *
 * <h3>容错策略</h3>
 * <ul>
 *   <li>列数不足 → 跳过整行，计入 skippedRows</li>
 *   <li>数字字段解析失败 → 使用默认值 0，计入 skippedRows</li>
 *   <li>日期字段为空 → 跳过整行</li>
 *   <li>空 actor → 返回 null（由调用方丢弃）</li>
 *   <li>坐标越界 → 置为 null（保留行，仅清洗坐标）</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see Event
 * @see EventCategory
 * @see GdeltImporter
 */
public class GdeltParser {

    // ==================== 列索引（0-based） ====================
    //
    // 同时兼容两种 GDELT 导出格式：
    //   61 列 —— GDELT 2.0 标准 export.CSV（地理区块含 ADM2 列）
    //   58 列 —— 旧版 GDELT 1.0 导出格式（地理区块无 ADM2 列）
    // 两者前 35 列（事件、参与者、事件编码、Goldstein、AvgTone）完全一致，
    // 差异只在地理区块与末尾列的位置。

    static final int COL_EVENT_ID       = 0;
    static final int COL_SQL_DATE       = 1;
    static final int COL_ACTOR1_CODE    = 7;
    static final int COL_ACTOR2_CODE    = 17;
    static final int COL_EVENT_CODE     = 26;
    static final int COL_EVENT_ROOT     = 28;
    static final int COL_GOLDSTEIN      = 30;
    static final int COL_NUM_MENTIONS   = 31;
    static final int COL_AVG_TONE       = 34;

    // 61 列（GDELT 2.0）格式的地理/URL 列位置
    static final int V2_COL_ACTION_GEO_LAT = 56;
    static final int V2_COL_ACTION_GEO_LON = 57;
    static final int V2_COL_SOURCEURL      = 60;

    // 58 列（旧版）格式的地理/URL 列位置
    static final int V1_COL_ACTION_GEO_LAT = 53;
    static final int V1_COL_ACTION_GEO_LON = 54;
    static final int V1_COL_SOURCEURL      = 57;

    /** 一行最少需要的列数（58 列旧格式即可解析） */
    static final int MIN_COLUMNS = 58;

    /** 经纬度有效范围 */
    private static final double MAX_LAT = 90.0;
    private static final double MAX_LON = 180.0;

    // ==================== 解析 ====================

    /**
     * 将 GDELT TSV 的一行字符串数组解析为 Event 对象。
     *
     * <p>返回 null 的情况（调用方应丢弃该行并计数）：</p>
     * <ul>
     *   <li>数组长度不足 {@value #MIN_COLUMNS}（58 列）</li>
     *   <li>event_id 无效</li>
     *   <li>event_date (SQLDATE) 为空</li>
     * </ul>
     *
     * @param row     TSV 分割后的字符串数组
     * @param skipper 跳过计数器（解析失败时自增）
     * @return 解析后的 Event 对象，或 null 表示该行应丢弃
     */
    public Event parse(String[] row, SkipCounter skipper) {
        // 列数校验
        if (row == null || row.length < MIN_COLUMNS) {
            if (skipper != null) skipper.shortRow++;
            return null;
        }

        // 61 列 → GDELT 2.0 标准格式；58~60 列 → 旧版格式（无 ADM2 列）
        final boolean isV2 = row.length >= 61;
        final int latCol    = isV2 ? V2_COL_ACTION_GEO_LAT : V1_COL_ACTION_GEO_LAT;
        final int lonCol    = isV2 ? V2_COL_ACTION_GEO_LON : V1_COL_ACTION_GEO_LON;
        final int sourceCol = isV2 ? V2_COL_SOURCEURL      : V1_COL_SOURCEURL;

        // event_id 解析
        long eventId;
        try {
            eventId = Long.parseLong(row[COL_EVENT_ID].trim());
        } catch (NumberFormatException e) {
            if (skipper != null) skipper.parseError++;
            return null;
        }

        // event_date 解析（yyyyMMdd → yyyy-MM-dd）
        String rawDate = row[COL_SQL_DATE].trim();
        if (rawDate.isEmpty()) {
            if (skipper != null) skipper.missingDate++;
            return null;
        }
        String eventDate = convertDate(rawDate);
        if (eventDate == null) {
            if (skipper != null) skipper.parseError++;
            return null;
        }

        // actor 代码（清洗：空字符串替换为 null 防止插入空串）
        String actor1 = nullIfEmpty(row[COL_ACTOR1_CODE].trim());
        String actor2 = nullIfEmpty(row[COL_ACTOR2_CODE].trim());

        // event_code / event_root
        String eventCode = row[COL_EVENT_CODE].trim();
        String eventRoot = row[COL_EVENT_ROOT].trim();

        // is_coop（派生字段，根据 event_root 计算）
        int isCoop = calcIsCoop(eventRoot);

        // goldstein
        double goldstein = parseDoubleSafe(row[COL_GOLDSTEIN]);
        if (skipper != null && Double.isNaN(goldstein)) {
            skipper.parseError++;
            goldstein = 0.0;
        }

        // num_mentions
        int numMentions = parseIntSafe(row[COL_NUM_MENTIONS]);
        if (skipper != null && numMentions == Integer.MIN_VALUE) {
            skipper.parseError++;
            numMentions = 0;
        }

        // avg_tone
        double avgTone = parseDoubleSafe(row[COL_AVG_TONE]);
        if (Double.isNaN(avgTone)) {
            avgTone = 0.0;
        }

        // 地理坐标（越界清洗：置 null）
        double lat = parseDoubleSafe(row[latCol]);
        double lon = parseDoubleSafe(row[lonCol]);
        boolean geoCleaned = false;

        if (Double.isNaN(lat) || Double.isNaN(lon)
                || Math.abs(lat) > MAX_LAT
                || Math.abs(lon) > MAX_LON) {
            lat = Double.NaN;
            lon = Double.NaN;
            geoCleaned = true;
        }

        // source_url
        String sourceUrl = nullIfEmpty(row[sourceCol].trim());

        // 构建 Event
        Event e = new Event(eventId, eventDate, actor1, actor2,
                eventRoot, eventCode, isCoop, goldstein, avgTone, numMentions,
                sourceUrl);

        if (!Double.isNaN(lat)) {
            e.setLat(lat);
            e.setLon(lon);
        } else if (skipper != null && geoCleaned) {
            skipper.geoCleaned++;
        }

        return e;
    }

    // ==================== 清洗方法（静态、可独立测试） ====================

    /**
     * 根据 CAMEO EventRootCode 计算 is_coop 标记（F-03）。
     *
     * @param eventRoot 事件大类编码，如 "04"、 "10"
     * @return 1=合作、0=冲突、-1=其他
     */
    public static int calcIsCoop(String eventRoot) {
        return EventCategory.fromEventRoot(eventRoot).getDbValue();
    }

    /**
     * 校验并清洗经纬度坐标。
     *
     * @param lat 原始纬度
     * @param lon 原始经度
     * @return {@code true} 如果坐标在合法范围内
     */
    public static boolean isValidGeo(double lat, double lon) {
        return !(Double.isNaN(lat) || Double.isNaN(lon))
                && Math.abs(lat) <= MAX_LAT
                && Math.abs(lon) <= MAX_LON;
    }

    /** 判断 actor 代码是否为空（null/空白/"0" 均视为空，GDELT 用 0 表示无国家归属） */
    public static boolean isActorEmpty(String actor) {
        return actor == null || actor.isBlank() || "0".equals(actor);
    }

    // ==================== 内部工具方法 ====================

    /** yyyyMMdd → yyyy-MM-dd，失败返回 null */
    static String convertDate(String raw) {
        if (raw.length() == 8) {
            return raw.substring(0, 4) + "-"
                    + raw.substring(4, 6) + "-"
                    + raw.substring(6, 8);
        }
        return null;
    }

    static double parseDoubleSafe(String s) {
        if (s == null || s.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    // ==================== 跳过计数器 ====================

    /**
     * 解析跳过计数器 — 在线程安全的前提下统计各类跳过原因。
     *
     * <p>各字段为 public 以便 Importer 聚合。</p>
     */
    public static class SkipCounter {
        /** 列数不足 */
        public int shortRow;
        /** 数字解析失败 */
        public int parseError;
        /** 日期字段为空 */
        public int missingDate;
        /** 坐标越界已清洗 */
        public int geoCleaned;
        /** 空 actor 丢弃（过滤阶段） */
        public int emptyActor;

        /** 全部跳过合计 */
        public int total() {
            return shortRow + parseError + missingDate;
        }

        /** 重置所有计数为零 */
        public void reset() {
            shortRow = 0;
            parseError = 0;
            missingDate = 0;
            geoCleaned = 0;
            emptyActor = 0;
        }
    }
}
