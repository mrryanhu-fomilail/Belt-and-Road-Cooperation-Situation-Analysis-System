package com.yourteam.bri.model;

/**
 * 合作线路数据模型 - 用于 MapPanel 中绘制国家间的合作连线
 * <p>
 * 每条连线代表两个国家间存在合作事件关联，在地图上以贝塞尔曲线（带箭头）绘制。
 * 连线的视觉属性根据业务数据动态映射：
 * <ul>
 *   <li>线宽（1~6px）= strength * 5 + 1，强度越大线条越粗</li>
 *   <li>颜色 = 根据 goldsteinScale 判定（深绿=强合作，浅绿=一般合作，红色=冲突）</li>
 *   <li>透明度 = 0.3 + strength * 0.6，强度越高越不透明</li>
 * </ul>
 * </p>
 *
 * @see com.yourteam.bri.gui.MapPanel#drawCooperationLines 连线的实际绘制方法
 */
public class CooperationLine {
    /** 起点国家三字母代码 */
    private String sourceCode;
    /** 起点国家纬度 */
    private double sourceLat;
    /** 起点国家经度 */
    private double sourceLon;
    /** 终点国家三字母代码 */
    private String targetCode;
    /** 终点国家纬度 */
    private double targetLat;
    /** 终点国家经度 */
    private double targetLon;
    /** 合作强度（0~1），决定连线粗细；值越大表示合作越紧密 */
    private double strength;
    /** Goldstein 量表值，决定连线颜色（正绿色/负红色）*/
    private double goldsteinScale;
    /** 两个国家间的关联事件总数 */
    private int eventCount;

    public CooperationLine() {}

    public CooperationLine(String sourceCode, double sourceLat, double sourceLon,
                           String targetCode, double targetLat, double targetLon,
                           double strength, double goldsteinScale, int eventCount) {
        this.sourceCode = sourceCode;
        this.sourceLat = sourceLat;
        this.sourceLon = sourceLon;
        this.targetCode = targetCode;
        this.targetLat = targetLat;
        this.targetLon = targetLon;
        this.strength = strength;
        this.goldsteinScale = goldsteinScale;
        this.eventCount = eventCount;
    }

    // Getters and Setters
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public double getSourceLat() { return sourceLat; }
    public void setSourceLat(double sourceLat) { this.sourceLat = sourceLat; }
    public double getSourceLon() { return sourceLon; }
    public void setSourceLon(double sourceLon) { this.sourceLon = sourceLon; }
    public String getTargetCode() { return targetCode; }
    public void setTargetCode(String targetCode) { this.targetCode = targetCode; }
    public double getTargetLat() { return targetLat; }
    public void setTargetLat(double targetLat) { this.targetLat = targetLat; }
    public double getTargetLon() { return targetLon; }
    public void setTargetLon(double targetLon) { this.targetLon = targetLon; }
    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = strength; }
    public double getGoldsteinScale() { return goldsteinScale; }
    public void setGoldsteinScale(double goldsteinScale) { this.goldsteinScale = goldsteinScale; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
}
