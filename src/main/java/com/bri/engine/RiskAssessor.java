package com.bri.engine;

/**
 * 风险评估纯函数工具类。
 *
 * <h3>公式</h3>
 * <ul>
 *   <li>riskScore = conflictRatio × 40 + (100 - coopIndex) × 0.3 + |negTrend| × 0.2</li>
 *   <li>分级：0~30 低风险 / 30~50 中风险 / 50~70 高风险 / 70+ 极高风险</li>
 * </ul>
 */
public final class RiskAssessor {

    private RiskAssessor() {}

    /** 计算风险评分 0~100 */
    public static double assess(double conflictRatio, double coopIndex, double trend) {
        double penalty = trend < 0 ? Math.min(Math.abs(trend), 100.0) : 0;
        return conflictRatio * 40.0 + (100.0 - coopIndex) * 0.3 + penalty * 0.2;
    }

    /** 风险等级 */
    public static String level(double score) {
        if (score < 30) return "低风险";
        if (score < 50) return "中风险";
        if (score < 70) return "高风险";
        return "极高风险";
    }

    /** 计算趋势变化率 */
    public static double trend(double current, double previous) {
        if (Math.abs(previous) < 1e-9) return current > 0 ? 100.0 : 0.0;
        return (current - previous) / previous * 100.0;
    }

    /** 识别拐点 */
    public static java.util.List<Integer> inflectionPoints(java.util.List<Double> series) {
        java.util.List<Integer> pts = new java.util.ArrayList<>();
        if (series == null || series.size() < 3) return pts;
        for (int i = 1; i < series.size() - 1; i++) {
            double a = series.get(i) - series.get(i - 1);
            double b = series.get(i + 1) - series.get(i);
            if (a * b < 0) pts.add(i);
        }
        return pts;
    }

    /** 情感分类 */
    public static String sentiment(double avgTone) {
        if (avgTone > 1.0) return "正面";
        if (avgTone < -1.0) return "负面";
        return "中性";
    }
}
