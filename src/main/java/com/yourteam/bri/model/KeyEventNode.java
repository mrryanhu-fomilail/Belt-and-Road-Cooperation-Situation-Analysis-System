package com.yourteam.bri.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 关键事件节点模型 - 用于 TimelinePanel 时间轴上的重大事件标记
 * <p>
 * 在时间轴上用星标（?）标记一带一路相关的重大历史事件，如高峰论坛、合作协议签署等。
 * 当用户悬停时显示标题和摘要，点击时弹出详细内容弹窗。
 * 播放模式到达事件日期时可触发 onKeyEventReached 回调通知。
 * </p>
 * <p>
 * 内置的关键事件示例：
 * <ul>
 *   <li>2019-04-26: 第二届一带一路高峰论坛</li>
 *   <li>2020-03-15: 疫情合作倡议（医疗物资援助）</li>
 *   <li>2023-10-17: 第三届一带一路高峰论坛</li>
 *   <li>2024-09-15: 一带一路合作新进展</li>
 * </ul>
 * </p>
 *
 * @see com.yourteam.bri.gui.TimelinePanel#initKeyEvents() 关键事件的初始化
 * @see com.yourteam.bri.gui.TimelinePanel.TimelineChangeListener#onKeyEventReached 事件到达回调
 */
public class KeyEventNode {
    /** 事件唯一标识（如 "2019-summit"）*/
    private String id;
    /** 事件发生日期 */
    private LocalDate date;
    /** 事件显示标题（在时间轴上方显示，过长会自动截断）*/
    private String title;
    /** 事件摘要（鼠标悬停时在 Tooltip 中显示）*/
    private String summary;
    /** 事件详细描述（点击时在弹窗中完整展示）*/
    private String description;
    /** 关联国家代码列表（用于多国联动分析）*/
    private List<String> relatedCountries;

    public KeyEventNode() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getRelatedCountries() { return relatedCountries; }
    public void setRelatedCountries(List<String> relatedCountries) { this.relatedCountries = relatedCountries; }
}
