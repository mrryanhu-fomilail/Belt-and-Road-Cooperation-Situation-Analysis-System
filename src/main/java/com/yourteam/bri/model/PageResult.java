package com.yourteam.bri.model;

import java.util.List;

/**
 * 泛型分页结果模型 - 封装分页查询的返回结果
 * <p>
 * 支持任意数据类型的泛型分页，用于新闻列表、事件查询结果等需要分页展示的场景。
 * 包含当前页数据列表（content）、页码、每页大小、总页数、总记录数等分页元信息。
 * 当前版本中主要在 NewsPanel 使用（每页 PAGE_SIZE=8 条），
 * 后续可扩展用于事件查询结果的真实分页。
 * </p>
 *
 * @param <T> 分页数据的实体类型
 * @see com.yourteam.bri.gui.NewsPanel 分页展示新闻卡片
 */
public class PageResult<T> {
    /** 当前页的数据列表 */
    private List<T> content;
    /** 当前页码（从 0 开始）*/
    private int page;
    /** 每页显示的数据条数 */
    private int pageSize;
    /** 总页数 = totalElements / pageSize 向上取整 */
    private int totalPages;
    /** 总记录数 */
    private long totalElements;

    public PageResult() {}

    public PageResult(List<T> content, int page, int pageSize, int totalPages, long totalElements) {
        this.content = content;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }

    // Getters and Setters
    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
}
