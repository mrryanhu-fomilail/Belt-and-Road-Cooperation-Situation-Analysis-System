package com.yourteam.bri.model;

import java.time.LocalDate;

/**
 * 新闻事件模型 - 封装单条新闻的标题、摘要、情感分析和关联国家信息
 * <p>
 * 用于右侧 NewsPanel 中新闻列表的展示与筛选。
 * 每条新闻包含情感分析结果（正面/负面/中性），支持按情感、国家、关键词过滤，
 * 以及分页展示。情感值通过 sentiment 字段量化，sentimentLabel 为分类标签。
 * </p>
 *
 * @see com.yourteam.bri.gui.NewsPanel 该模型的主要使用方
 */
public class NewsEvent {
    /** 新闻唯一标识 ID */
    private int newsId;
    /** 新闻标题 */
    private String title;
    /** 新闻摘要/简介 */
    private String summary;
    /** 新闻发布日期 */
    private LocalDate newsDate;
    /** 关联国家的三字母代码 */
    private String countryCode;
    /** 关联国家的中文名称 */
    private String countryName;
    /** 关联城市名称（预留城市级过滤功能）*/
    private String cityName;
    /** 新闻情感量化值（正值=正面，负值=负面，范围约 -5~5）*/
    private double sentiment;
    /** 情感分类标签："正面" / "负面" / "中性"（由 sentiment 值判定）*/
    private String sentimentLabel;
    /** Goldstein 量表值，反映事件的合作/冲突倾向 */
    private double goldsteinScale;
    /** 媒体引用次数，反映新闻的重要程度 */
    private int numMentions;
    /** 新闻来源 URL */
    private String sourceUrl;

    public NewsEvent() {}

    // Getters and Setters
    public int getNewsId() { return newsId; }
    public void setNewsId(int newsId) { this.newsId = newsId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDate getNewsDate() { return newsDate; }
    public void setNewsDate(LocalDate newsDate) { this.newsDate = newsDate; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public double getSentiment() { return sentiment; }
    public void setSentiment(double sentiment) { this.sentiment = sentiment; }
    public String getSentimentLabel() { return sentimentLabel; }
    public void setSentimentLabel(String sentimentLabel) { this.sentimentLabel = sentimentLabel; }
    public double getGoldsteinScale() { return goldsteinScale; }
    public void setGoldsteinScale(double goldsteinScale) { this.goldsteinScale = goldsteinScale; }
    public int getNumMentions() { return numMentions; }
    public void setNumMentions(int numMentions) { this.numMentions = numMentions; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
}
