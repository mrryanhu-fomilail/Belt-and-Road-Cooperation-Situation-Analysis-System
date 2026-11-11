package com.bri.engine;

import java.time.YearMonth;
import java.util.Objects;

/**
 * 预计算的月度聚合统计 — 空间换时间的核心数据结构。
 *
 * <p>把某国某月的事件全部预聚合为 7 个 double，避免每次查询都遍历原始事件。
 * 内存占用估算：10年×12月×80国×48字节 ≈ 460KB，可忽略。</p>
 *
 * <h3>为什么这样设计</h3>
 * <ul>
 *   <li>原始查询 O(N) → 索引后 O(1)，HashMap key = "PAK#2026-06"</li>
 *   <li>所有指标直接存储，不需回溯原始事件</li>
 *   <li>不可变对象，线程安全，多前端组件可并发读取</li>
 * </ul>
 */
public class MonthlyStats {
    /** 合作指数 (0~100) */
    public final double coopIndex;
    /** 冲突比例 (0~1) */
    public final double conflictRatio;
    /** 情感均值 (-10~+10) */
    public final double avgTone;
    /** 事件总数 */
    public final int eventCount;
    /** 合作事件数 */
    public final int coopCount;
    /** 冲突事件数 */
    public final int conflictCount;
    /** CAMEO 编码分布 ("04"→120, "10"→45, ...) */
    public final java.util.Map<String, Integer> cameoDist;

    public MonthlyStats(double coopIndex, double conflictRatio, double avgTone,
                        int eventCount, int coopCount, int conflictCount,
                        java.util.Map<String, Integer> cameoDist) {
        this.coopIndex = coopIndex;
        this.conflictRatio = conflictRatio;
        this.avgTone = avgTone;
        this.eventCount = eventCount;
        this.coopCount = coopCount;
        this.conflictCount = conflictCount;
        this.cameoDist = java.util.Collections.unmodifiableMap(cameoDist);
    }

    /** 构建索引 key: "PAK#2026-06" */
    public static String key(String countryCode, YearMonth month) {
        return countryCode + "#" + month.toString();
    }

    public static String key(String countryCode, int year, int month) {
        return countryCode + "#" + year + "-" + String.format("%02d", month);
    }

    /** 字符串月 key 直接拼接，如 key("PAK", "2026-06") */
    public static String key(String countryCode, String yearMonth) {
        return countryCode + "#" + yearMonth;
    }

    @Override
    public String toString() {
        return String.format("coop=%.1f conflict=%.2f tone=%.1f events=%d",
                coopIndex, conflictRatio, avgTone, eventCount);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MonthlyStats)) return false;
        MonthlyStats s = (MonthlyStats) o;
        return Double.compare(s.coopIndex, coopIndex) == 0
                && Double.compare(s.conflictRatio, conflictRatio) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(coopIndex, conflictRatio);
    }
}
