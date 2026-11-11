package com.bri.dao;

import com.bri.model.Event;
import com.bri.model.MonthlyTrend;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 事件数据访问接口 — 对外暴露的事件查询契约。
 *
 * <p>所有方法均为线程安全，可在后台线程调用。
 * 空结果返回空 {@link List}（非 {@code null}）；
 * 异常统一包装为 {@link DataAccessException}。</p>
 *
 * <h3>功能覆盖</h3>
 * <ul>
 *   <li>F-06 组合条件查询 — {@link #query(EventQuery)}</li>
 *   <li>F-07 双边关系查询 — {@link #queryBilateral}</li>
 *   <li>F-08 子区域查询 — {@link #queryBySubregion}</li>
 *   <li>F-09/F-10 按事件大类筛选 — {@link #queryByRoots}</li>
 *   <li>F-13 月度聚合趋势 — {@link #monthlyCount}</li>
 *   <li>F-18 仪表盘总量 — {@link #countEvents}</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.bri.model.Event
 * @see EventQuery
 * @see DaoFactory
 */
public interface EventDao {

    /**
     * 组合条件查询（F-06）。
     * <p>任意条件为 {@code null} 表示不限制该条件。</p>
     *
     * @param q 查询参数对象
     * @return 匹配的事件列表，无结果为空 List
     * @throws DataAccessException 数据库异常
     */
    List<Event> query(EventQuery q);

    /**
     * 双边关系查询（F-07）。
     * <p>查询两国之间所有交互事件，返回双向（c1→c2 或 c2→c1），按日期升序排列。</p>
     *
     * @param c1   国家1 CAMEO 码
     * @param c2   国家2 CAMEO 码
     * @param from 起始日期 yyyy-MM-dd（含）
     * @param to   截止日期 yyyy-MM-dd（含）
     * @return 两国间交互事件列表，按日期升序
     * @throws DataAccessException 数据库异常
     */
    List<Event> queryBilateral(String c1, String c2, String from, String to);

    /**
     * 子区域内所有事件查询（F-08）。
     * <p>查询某子区域内所有沿线国家间的事件。</p>
     *
     * @param subKey 子区域键，如 "SEA"
     * @param from   起始日期 yyyy-MM-dd（含）
     * @param to     截止日期 yyyy-MM-dd（含）
     * @return 该子区域内的事件列表
     * @throws DataAccessException 数据库异常
     */
    List<Event> queryBySubregion(String subKey, String from, String to);

    /**
     * 按事件大类集合筛选（F-09/F-10）。
     * <p>筛选 EventRootCode 在指定集合中的事件。
     * 如合作类传 ["04","05","06"]，冲突类传 ["08","09","10","11","12","13","14"]。</p>
     *
     * @param roots 事件大类编码列表
     * @param from  起始日期 yyyy-MM-dd（含）
     * @param to    截止日期 yyyy-MM-dd（含）
     * @return 匹配的事件列表
     * @throws DataAccessException 数据库异常
     */
    List<Event> queryByRoots(List<String> roots, String from, String to);

    /**
     * 按月聚合某国参与的事件数量（F-13 趋势分析用）。
     * <p>返回 Map 的 key 为月份字符串 {@code yyyy-MM}，value 为该月事件数。</p>
     *
     * @param country  CAMEO 国家码
     * @param coopOnly {@code true} 只统计合作事件，{@code false} 统计全部
     * @return 月份 → 事件数 的映射，按月份升序
     * @throws DataAccessException 数据库异常
     */
    Map<String, Integer> monthlyCount(String country, boolean coopOnly);

    /**
     * 统计事件总量（F-18 仪表盘用）。
     *
     * @param coopOnly {@code true} 只统计合作事件，{@code false} 统计全部
     * @return 事件数量
     * @throws DataAccessException 数据库异常
     */
    long countEvents(boolean coopOnly);

    /**
     * 获取数据库中事件日期范围（最小日期和最大日期）。
     *
     * @return 长度为 2 的 LocalDate 数组 [minDate, maxDate]，
     *         无数据时返回 [当前年月第一天, 当前年月最后一天]
     * @throws DataAccessException 数据库异常
     */
    LocalDate[] getDateRange();

    /**
     * 按月统计某国与中国（CHN）的双边月度合作指标（F-19 趋势分析用）。
     * <p>统计中国与指定国家之间 each 月的：
     * <ul>
     *   <li>事件数量</li>
     *   <li>Goldstein 均值（衡量合作/冲突倾向）</li>
     *   <li>情感语调均值（avg_tone）</li>
     * </ul>
     * 返回按月份升序排列的列表。</p>
     *
     * @param countryCode 国家 CAMEO 码
     * @param from        起始日期 yyyy-MM-dd
     * @param to          截止日期 yyyy-MM-dd
     * @return 月度指标列表（按月份升序），无数据时为空列表
     * @throws DataAccessException 数据库异常
     */
    List<MonthlyTrend> monthlyBilateralTrend(String countryCode, String from, String to);
}

