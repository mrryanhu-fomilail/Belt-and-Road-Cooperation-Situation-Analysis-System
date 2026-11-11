package com.bri.model;

/** 新闻条目，对应 news.json */
public class NewsItem {
    private String id;
    private String title;
    private String summary;
    private String content;
    private String countryCode;
    private String date;         // yyyy-MM-dd
    private String sentiment;    // "正面"/"负面"/"中性"
    private int numMentions;

    public NewsItem() {}

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public String getDate() { return date; }
    public void setDate(String v) { this.date = v; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String v) { this.sentiment = v; }
    public int getNumMentions() { return numMentions; }
    public void setNumMentions(int v) { this.numMentions = v; }
}
