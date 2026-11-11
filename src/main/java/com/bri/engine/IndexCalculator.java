package com.bri.engine;

import com.bri.model.Event;
import java.util.List;
import java.util.Map;

/**
 * 指数计算策略接口 — 体现开闭原则（对扩展开放，对修改关闭）。
 *
 * <p>如果想换合作指数公式，只需新增一个实现类，不改动任何已有代码。
 * 例如：老师觉得原公式不好 → 写个 {@code WeightedIndexCalculator} 实现本接口，
 * 注入到 {@link BriAnalysisEngine} 即可。</p>
 *
 * <h3>扩展示例</h3>
 * <pre>{@code
 * // 原公式
 * new DefaultIndexCalculator()
 *
 * // 新公式：加权版（只需新增类，不改任何旧代码）
 * new WeightedIndexCalculator(0.4, 0.3, 0.3)
 *
 * // 新公式：机器学习版
 * new MLIndexCalculator("model.pmml")
 * }</pre>
 */
public interface IndexCalculator {

    /**
     * 从事件列表计算合作指数 (0~100)。
     */
    double calculateCoopIndex(List<Event> events);

    /**
     * 从事件列表计算冲突比例 (0~1)。
     * 默认实现直接可用接口的 default 方法，但允许覆盖。
     */
    default double calculateConflictRatio(List<Event> events) {
        if (events == null || events.isEmpty()) return 0.0;
        long cnt = events.stream().filter(Event::isConflict).count();
        return (double) cnt / events.size();
    }

    /**
     * 从事件列表计算情感均值 (-10~+10)。
     */
    default double calculateAvgTone(List<Event> events) {
        if (events == null || events.isEmpty()) return 0.0;
        return events.stream().mapToDouble(Event::getAvgTone).average().orElse(0.0);
    }

    /**
     * 按 CAMEO EventRoot 分类统计。
     */
    default Map<String, Integer> classifyCameo(List<Event> events) {
        java.util.Map<String, Integer> result = new java.util.TreeMap<>();
        if (events == null) return result;
        for (Event e : events) {
            String root = e.getEventRoot();
            if (root != null && !root.isEmpty())
                result.merge(root, 1, Integer::sum);
        }
        return result;
    }

    /**
     * 一次性计算全部月度聚合，避免多次遍历同一事件列表。
     *
     * <p>这是空间换时间的关键：一次遍历同时算出 cooperIndex + conflictRatio +
     * avgTone + cameoDist + eventCount，存入 {@link MonthlyStats}。</p>
     */
    MonthlyStats computeAll(List<Event> events);
}
