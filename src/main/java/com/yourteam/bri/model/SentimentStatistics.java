package com.yourteam.bri.model;

/**
 * 情感统计分析模型 - 统计新闻或事件的情感倾向分布
 * <p>
 * 对一批新闻/事件进行情感分析后，统计正面、负面、中性各分类的数量和比例。
 * 用于 NewsPanel 顶部统计显示（如"共N条 ??X ??Y ??Z"），
 * 也可扩展用于 QueryPanel 的情感分布饼图。
 * 各比例值之和应为 1.0（或 100%）。
 * </p>
 *
 * @see com.yourteam.bri.gui.NewsPanel 新闻列表的情感统计展示
 */
public class SentimentStatistics {
    /** 正面情感（sentiment > 2.0）的新闻/事件数量 */
    private int positiveCount;
    /** 负面情感（sentiment < -2.0）的新闻/事件数量 */
    private int negativeCount;
    /** 中性情感（-2.0 <= sentiment <= 2.0）的新闻/事件数量 */
    private int neutralCount;
    /** 正面占比（0~1），positiveCount / total */
    private double positiveRatio;
    /** 负面占比（0~1）*/
    private double negativeRatio;
    /** 中性占比（0~1）*/
    private double neutralRatio;

    public SentimentStatistics() {}

    public SentimentStatistics(int positiveCount, int negativeCount, int neutralCount,
                               double positiveRatio, double negativeRatio, double neutralRatio) {
        this.positiveCount = positiveCount;
        this.negativeCount = negativeCount;
        this.neutralCount = neutralCount;
        this.positiveRatio = positiveRatio;
        this.negativeRatio = negativeRatio;
        this.neutralRatio = neutralRatio;
    }

    // Getters and Setters
    public int getPositiveCount() { return positiveCount; }
    public void setPositiveCount(int positiveCount) { this.positiveCount = positiveCount; }
    public int getNegativeCount() { return negativeCount; }
    public void setNegativeCount(int negativeCount) { this.negativeCount = negativeCount; }
    public int getNeutralCount() { return neutralCount; }
    public void setNeutralCount(int neutralCount) { this.neutralCount = neutralCount; }
    public double getPositiveRatio() { return positiveRatio; }
    public void setPositiveRatio(double positiveRatio) { this.positiveRatio = positiveRatio; }
    public double getNegativeRatio() { return negativeRatio; }
    public void setNegativeRatio(double negativeRatio) { this.negativeRatio = negativeRatio; }
    public double getNeutralRatio() { return neutralRatio; }
    public void setNeutralRatio(double neutralRatio) { this.neutralRatio = neutralRatio; }
}
