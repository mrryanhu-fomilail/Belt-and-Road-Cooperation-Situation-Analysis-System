package com.yourteam.bri.model;

/**
 * 国家数据模型 - 一带一路沿线国家的地理信息与动态指标封装
 * <p>
 * 包含国家的基本信息（代码、名称、区域）、地图渲染所需的经纬度坐标，
 * 以及动态更新的合作指数、风险等级、聚类标签等分析指标。
 * 该模型用于地图面板的泡泡图渲染、排行榜展示、聚类分析等多个功能模块。
 * </p>
 *
 * @see com.yourteam.bri.gui.MapPanel 使用该模型渲染世界地图泡泡
 * @see com.yourteam.bri.gui.RankingPanel 使用该模型的合作指数进行排名展示
 */
public class Country {
    /** 国家三字母代码（ISO 3166-1 alpha-3），如 "CHN" 代表中国 */
    private String code;
    /** 国家中文名称 */
    private String name;
    /** 所属区域，如 "东南亚"、"中亚"、"中东"、"欧洲"、"非洲"、"南亚" */
    private String region;
    /** 纬度坐标（用于地图定位）*/
    private double latitude;
    /** 经度坐标（用于地图定位）*/
    private double longitude;
    /** 是否为一带一路沿线国家（除中国外默认为 true）*/
    private boolean isBeltAndRoad;
    /** 合作指数（动态计算填充），范围 0~1，值越高表示合作越紧密 */
    private double cooperationIndex;
    /** 风险等级（动态计算填充），如 "低"、"中"、"高"、"极高" */
    private String riskLevel;
    /** 聚类标签（动态计算填充），如 "深度合作伙伴"、"稳定合作"、"存在风险"、"高度紧张" */
    private String clusterLabel;

    public Country() {}

    public Country(String code, String name, String region, double latitude, double longitude, boolean isBeltAndRoad) {
        this.code = code;
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isBeltAndRoad = isBeltAndRoad;
    }

    // Getters and Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public boolean isBeltAndRoad() { return isBeltAndRoad; }
    public void setBeltAndRoad(boolean beltAndRoad) { isBeltAndRoad = beltAndRoad; }
    public double getCooperationIndex() { return cooperationIndex; }
    public void setCooperationIndex(double cooperationIndex) { this.cooperationIndex = cooperationIndex; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getClusterLabel() { return clusterLabel; }
    public void setClusterLabel(String clusterLabel) { this.clusterLabel = clusterLabel; }
}

