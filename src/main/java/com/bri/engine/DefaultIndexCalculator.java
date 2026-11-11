package com.bri.engine;

import com.bri.model.Event;

import java.util.*;

/**
 * 默认指数计算策略 — 一次遍历完成全部聚合（空间换时间）。
 *
 * <h3>公式</h3>
 * <ul>
 *   <li>coopIndex = Σ(Goldstein × log(1+Mentions)) / N × 10 + 5 → 映射到 0~100</li>
 *   <li>conflictRatio = 冲突事件数 / 总事件数</li>
 *   <li>avgTone = Σ(AvgTone) / N</li>
 * </ul>
 */
public class DefaultIndexCalculator implements IndexCalculator {

    @Override
    public double calculateCoopIndex(List<Event> events) {
        if (events == null || events.isEmpty()) return 50.0;
        double sum = 0.0;
        for (Event e : events)
            sum += e.getGoldstein() * Math.log(1.0 + Math.max(0, e.getNumMentions()));
        double raw = (sum / events.size()) * 10.0 + 5.0;
        return clamp(raw * 5.0, 0.0, 100.0);
    }

    /**
     * 核心优化：一次遍历算出所有指标，避免反复扫描同一个事件列表。
     *
     * <p>原来的做法：调 calculateCoopIndex 扫一遍，calculateConflictRatio 再扫一遍，
     * calculateAvgTone 又扫一遍 → 3 次 O(N)。
     * 现在：扫一遍同时更新 7 个累加器 → 1 次 O(N)。</p>
     */
    @Override
    public MonthlyStats computeAll(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return new MonthlyStats(50.0, 0.0, 0.0, 0, 0, 0, Collections.emptyMap());
        }

        double goldsteinSum = 0.0;
        double toneSum = 0.0;
        int coopCount = 0;
        int conflictCount = 0;
        Map<String, Integer> cameoDist = new HashMap<>();

        for (Event e : events) {
            // 合作指数分子累加
            goldsteinSum += e.getGoldstein() * Math.log(1.0 + Math.max(0, e.getNumMentions()));

            // 情感累加
            toneSum += e.getAvgTone();

            // 分类统计
            if (e.isCooperative()) coopCount++;
            if (e.isConflict()) conflictCount++;

            // CAMEO 分布
            String root = e.getEventRoot();
            if (root != null && !root.isEmpty())
                cameoDist.merge(root, 1, Integer::sum);
        }

        int n = events.size();
        double raw = (goldsteinSum / n) * 10.0 + 5.0;
        double coopIndex = clamp(raw * 5.0, 0.0, 100.0);
        double conflictRatio = (double) conflictCount / n;
        double avgTone = toneSum / n;

        return new MonthlyStats(coopIndex, conflictRatio, avgTone,
                n, coopCount, conflictCount, cameoDist);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
