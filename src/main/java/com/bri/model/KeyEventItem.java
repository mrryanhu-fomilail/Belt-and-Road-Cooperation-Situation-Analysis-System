package com.bri.model;

import java.util.List;

/** 关键事件（时间轴），对应 events.json */
public class KeyEventItem {
    private String id;
    private String title;
    private String summary;
    private String description;
    private String date;                    // yyyy-MM-dd
    private List<String> relatedCountries;

    public KeyEventItem() {}

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getDate() { return date; }
    public void setDate(String v) { this.date = v; }
    public List<String> getRelatedCountries() { return relatedCountries; }
    public void setRelatedCountries(List<String> v) { this.relatedCountries = v; }
}
