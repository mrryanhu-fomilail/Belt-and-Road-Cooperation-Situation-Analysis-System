package com.yourteam.bri.model;

/**
 * 新闻详情模型 - 继承 NewsEvent，增加新闻完整正文内容
 * <p>
 * 当用户在 NewsPanel 中点击某条新闻卡片时，系统会加载该新闻的完整详情。
 * fullContent 字段存储新闻的全文正文，支持在弹窗对话框中完整展示。
 * </p>
 *
 * @see com.yourteam.bri.gui.NewsPanel#showNewsDetail(NewsEvent) 详情弹窗的调用入口
 */
public class NewsDetail extends NewsEvent {
    /** 新闻的完整正文内容（包含段落格式文本）*/
    private String fullContent;

    public NewsDetail() {}

    public String getFullContent() { return fullContent; }
    public void setFullContent(String fullContent) { this.fullContent = fullContent; }
}
