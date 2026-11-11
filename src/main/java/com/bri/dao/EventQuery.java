package com.bri.dao;

/**
 * 事件组合查询参数对象，采用建造者模式 (Builder Pattern) 构建。
 *
 * <p>所有查询条件均可选：为 {@code null} 时表示"不限制该条件"。
 * 日期格式统一为 ISO 字符串 {@code yyyy-MM-dd}。</p>
 *
 * <h3>用法示例</h3>
 * <pre>{@code
 * EventQuery q = new EventQuery.Builder()
 *     .dateFrom("2025-01-01")
 *     .dateTo("2025-12-31")
 *     .actor1("CHN")
 *     .isCoop(1)
 *     .build();
 * List<Event> results = dao.query(q);
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.bri.dao.EventDao#query(EventQuery)
 */
public class EventQuery {

    /** 起始日期 (yyyy-MM-dd)，null = 不限 */
    private String dateFrom;

    /** 截止日期 (yyyy-MM-dd)，null = 不限 */
    private String dateTo;

    /** 施动国 CAMEO 码，null = 不限 */
    private String actor1;

    /** 受动国 CAMEO 码，null = 不限 */
    private String actor2;

    /** 合作/冲突标记：1=合作、0=冲突、null=不限 */
    private Integer isCoop;

    /** 子区域键，null = 不限 */
    private String subregion;

    /** 返回条数上限，null = 不限 */
    private Integer limit;

    // 私有构造，仅通过 Builder 创建
    private EventQuery() {}

    // ==================== Getters ====================

    /** @return 起始日期 */
    public String getDateFrom() { return dateFrom; }

    /** @return 截止日期 */
    public String getDateTo() { return dateTo; }

    /** @return 施动国 CAMEO 码 */
    public String getActor1() { return actor1; }

    /** @return 受动国 CAMEO 码 */
    public String getActor2() { return actor2; }

    /** @return 合作/冲突标记 */
    public Integer getIsCoop() { return isCoop; }

    /** @return 子区域键 */
    public String getSubregion() { return subregion; }

    /** @return 返回条数上限 */
    public Integer getLimit() { return limit; }

    /**
     * 判断是否有任何查询条件被设置。
     *
     * @return {@code true} 如果所有条件均为 null
     */
    public boolean isEmpty() {
        return dateFrom == null && dateTo == null
                && actor1 == null && actor2 == null
                && isCoop == null && subregion == null && limit == null;
    }

    /**
     * EventQuery 建造器。
     */
    public static class Builder {
        private final EventQuery query = new EventQuery();

        /** @param dateFrom 起始日期 (yyyy-MM-dd) */
        public Builder dateFrom(String dateFrom) {
            query.dateFrom = dateFrom;
            return this;
        }

        /** @param dateTo 截止日期 (yyyy-MM-dd) */
        public Builder dateTo(String dateTo) {
            query.dateTo = dateTo;
            return this;
        }

        /** @param actor1 施动国 CAMEO 码 */
        public Builder actor1(String actor1) {
            query.actor1 = actor1;
            return this;
        }

        /** @param actor2 受动国 CAMEO 码 */
        public Builder actor2(String actor2) {
            query.actor2 = actor2;
            return this;
        }

        /** @param isCoop 1=合作、0=冲突、null=不限 */
        public Builder isCoop(Integer isCoop) {
            query.isCoop = isCoop;
            return this;
        }

        /** @param subregion 子区域键，如 "SEA" */
        public Builder subregion(String subregion) {
            query.subregion = subregion;
            return this;
        }

        /** @param limit 返回条数上限 */
        public Builder limit(Integer limit) {
            query.limit = limit;
            return this;
        }

        /**
         * 构建 EventQuery 实例。
         *
         * @return 不可变的查询参数对象
         */
        public EventQuery build() {
            return query;
        }
    }
}
