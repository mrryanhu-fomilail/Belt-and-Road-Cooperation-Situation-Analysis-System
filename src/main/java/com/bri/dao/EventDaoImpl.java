package com.bri.dao;

import com.bri.config.DataSource;
import com.bri.model.Event;
import com.bri.model.MonthlyTrend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link EventDao} 的 JDBC 实现 — 事件查询的完整数据访问逻辑。
 *
 * <p>所有 SQL 使用 {@link PreparedStatement} 动态拼接，防止注入。
 * 每个方法独立获取/归还连接，天然线程安全。</p>
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>动态 WHERE 子句仅在条件非 null 时拼接（避免冗余 AND）</li>
 *   <li>子区域查询通过子查询关联 countries 表</li>
 *   <li>月度聚合使用 {@code substr(event_date, 1, 7)}</li>
 *   <li>空结果返回空 List/Map，永远不返回 null</li>
 * </ul>
 *
 * <h3>使用示例（队友参考）</h3>
 * <pre>{@code
 * DaoFactory f = DaoFactory.getInstance("bri.db");
 * EventDao dao = f.eventDao();
 *
 * // F-06 组合查询
 * EventQuery q = new EventQuery.Builder()
 *     .dateFrom("2025-01-01").dateTo("2025-12-31")
 *     .actor1("CHN").isCoop(1).build();
 * List<Event> list = dao.query(q);
 *
 * // F-07 双边关系
 * List<Event> bilateral = dao.queryBilateral("CHN", "PAK", "2025-01-01", "2025-12-31");
 *
 * // F-08 子区域
 * List<Event> sea = dao.queryBySubregion("SEA", "2025-06-01", "2025-06-30");
 *
 * // F-13 月度趋势
 * Map<String,Integer> trend = dao.monthlyCount("KAZ", true);
 *
 * // F-18 仪表盘
 * long coop = dao.countEvents(true);
 * long conflict = dao.countEvents(false);
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see EventDao
 * @see EventQuery
 * @see Event
 */
public class EventDaoImpl implements EventDao {

    /** 数据源（连接池） */
    private final DataSource ds;

    /**
     * 构造 DAO 实现。
     *
     * @param ds 数据库连接池
     */
    public EventDaoImpl(DataSource ds) {
        this.ds = Objects.requireNonNull(ds, "DataSource 不能为 null");
    }

    // ==================== F-06: 组合查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p>所有条件可选。当所有条件为 null 时返回全部事件。</p>
     */
    @Override
    public List<Event> query(EventQuery q) {
        if (q == null) {
            q = new EventQuery.Builder().build();
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // 日期范围
        if (q.getDateFrom() != null) {
            sql.append(" AND event_date >= ?");
            params.add(q.getDateFrom());
        }
        if (q.getDateTo() != null) {
            sql.append(" AND event_date <= ?");
            params.add(q.getDateTo());
        }

        // 国家筛选
        if (q.getActor1() != null) {
            sql.append(" AND actor1_code = ?");
            params.add(q.getActor1());
        }
        if (q.getActor2() != null) {
            sql.append(" AND actor2_code = ?");
            params.add(q.getActor2());
        }

        // 合作/冲突标记
        if (q.getIsCoop() != null) {
            sql.append(" AND is_coop = ?");
            params.add(q.getIsCoop());
        }

        // 子区域（关联 countries 表）
        if (q.getSubregion() != null) {
            sql.append(" AND (actor1_code IN (SELECT cameo_code FROM countries WHERE subregion = ?)")
                    .append("  OR actor2_code IN (SELECT cameo_code FROM countries WHERE subregion = ?))");
            params.add(q.getSubregion());
            params.add(q.getSubregion());
        }

        // 排序
        sql.append(" ORDER BY event_date DESC, event_id DESC");

        // 分页
        if (q.getLimit() != null) {
            sql.append(" LIMIT ?");
            params.add(q.getLimit());
        }

        return executeQuery(sql.toString(), params);
    }

    // ==================== F-07: 双边关系 ====================

    /**
     * {@inheritDoc}
     *
     * <p>查询 c1→c2 和 c2→c1 两个方向的事件，按日期升序。</p>
     */
    @Override
    public List<Event> queryBilateral(String c1, String c2,
                                      String from, String to) {
        Objects.requireNonNull(c1, "国家1 不能为 null");
        Objects.requireNonNull(c2, "国家2 不能为 null");

        String sql = "SELECT * FROM events"
                + " WHERE ( (actor1_code = ? AND actor2_code = ?)"
                + "      OR (actor1_code = ? AND actor2_code = ?) )"
                + "   AND event_date >= ?"
                + "   AND event_date <= ?"
                + " ORDER BY event_date ASC, event_id ASC";

        return executeQuery(sql, List.of(c1, c2, c2, c1, from, to));
    }

    // ==================== F-08: 子区域查询 ====================

    /**
     * {@inheritDoc}
     *
     * <p>查询某子区域内所有沿线国家参与的事件（作为 actor1 或 actor2）。</p>
     */
    @Override
    public List<Event> queryBySubregion(String subKey,
                                        String from, String to) {
        Objects.requireNonNull(subKey, "子区域键不能为 null");

        String sql = "SELECT * FROM events"
                + " WHERE (actor1_code IN (SELECT cameo_code FROM countries WHERE subregion = ?)"
                + "     OR actor2_code IN (SELECT cameo_code FROM countries WHERE subregion = ?))"
                + "   AND event_date >= ?"
                + "   AND event_date <= ?"
                + " ORDER BY event_date DESC, event_id DESC";

        return executeQuery(sql, List.of(subKey, subKey, from, to));
    }

    // ==================== F-09/F-10: 按事件大类筛选 ====================

    /**
     * {@inheritDoc}
     *
     * <p>构建 {@code WHERE event_root IN (?,?,...)} 子句。
     * 如果 roots 为空或 null，返回空列表。</p>
     */
    @Override
    public List<Event> queryByRoots(List<String> roots,
                                    String from, String to) {
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = roots.stream()
                .map(r -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT * FROM events"
                + " WHERE event_root IN (" + placeholders + ")"
                + "   AND event_date >= ?"
                + "   AND event_date <= ?"
                + " ORDER BY event_date DESC, event_id DESC";

        List<Object> params = new ArrayList<>(roots);
        params.add(from);
        params.add(to);

        return executeQuery(sql, params);
    }

    // ==================== F-13: 月度聚合 ====================

    /**
     * {@inheritDoc}
     *
     * <p>使用 {@code substr(event_date, 1, 7)} 提取 yyyy-MM，
     * GROUP BY 后按月份升序返回。</p>
     */
    @Override
    public Map<String, Integer> monthlyCount(String country,
                                             boolean coopOnly) {
        Objects.requireNonNull(country, "国家码不能为 null");

        StringBuilder sql = new StringBuilder(
                "SELECT substr(event_date, 1, 7) AS month, COUNT(*) AS cnt"
                        + " FROM events"
                        + " WHERE (actor1_code = ? OR actor2_code = ?)");

        List<Object> params = new ArrayList<>();
        params.add(country);
        params.add(country);

        if (coopOnly) {
            sql.append(" AND is_coop = 1");
        }

        sql.append(" GROUP BY month ORDER BY month ASC");

        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("month"), rs.getInt("cnt"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "月度聚合查询失败: country=" + country, e);
        }
        return result;
    }

    // ==================== F-18: 事件总量 ====================

    /**
     * {@inheritDoc}
     *
     * @param coopOnly {@code true} 只统计合作事件；{@code false} 统计全部
     */
    @Override
    public long countEvents(boolean coopOnly) {
        String sql = coopOnly
                ? "SELECT COUNT(*) FROM events WHERE is_coop = 1"
                : "SELECT COUNT(*) FROM events";

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new DataAccessException("事件总量统计失败", e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 执行参数化查询并将 ResultSet 映射为 Event 列表。
     *
     * @param sql    预编译 SQL
     * @param params 参数列表
     * @return 映射后的 Event 列表（可能为空列表）
     */
    private List<Event> executeQuery(String sql, List<Object> params) {
        List<Event> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                    "事件查询失败: " + sql.substring(0, Math.min(80, sql.length())), e);
        }
        return result;
    }

    /**
     * 将 ResultSet 当前行映射为 Event 对象。
     */
    static Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setEventId(rs.getLong("event_id"));
        e.setEventDate(rs.getString("event_date"));
        e.setActor1Code(rs.getString("actor1_code"));
        e.setActor2Code(rs.getString("actor2_code"));
        e.setEventRoot(rs.getString("event_root"));
        e.setEventCode(rs.getString("event_code"));
        e.setIsCoop(rs.getInt("is_coop"));
        e.setGoldstein(rs.getDouble("goldstein"));
        e.setAvgTone(rs.getDouble("avg_tone"));
        e.setNumMentions(rs.getInt("num_mentions"));

        String url = rs.getString("source_url");
        if (!rs.wasNull()) e.setSourceUrl(url);

        double lat = rs.getDouble("lat");
        if (!rs.wasNull()) e.setLat(lat);

        double lon = rs.getDouble("lon");
        if (!rs.wasNull()) e.setLon(lon);

        return e;
    }

    // ==================== 日期范围查询 ====================

    @Override
    public LocalDate[] getDateRange() {
        String sql = "SELECT MIN(event_date) AS min_date, MAX(event_date) AS max_date FROM events";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String minStr = rs.getString("min_date");
                String maxStr = rs.getString("max_date");
                LocalDate minDate = (minStr != null && minStr.length() >= 10)
                    ? LocalDate.parse(minStr.substring(0, 10))
                    : LocalDate.of(LocalDate.now().getYear(), 1, 1);
                LocalDate maxDate = (maxStr != null && maxStr.length() >= 10)
                    ? LocalDate.parse(maxStr.substring(0, 10))
                    : LocalDate.now();
                return new LocalDate[]{minDate, maxDate};
            }
            return new LocalDate[]{LocalDate.now().minusMonths(6), LocalDate.now()};
        } catch (SQLException e) {
            throw new DataAccessException("日期范围查询失败", e);
        }
    }

    // ==================== F-19: 月度双边趋势 ====================

    @Override
    public List<MonthlyTrend> monthlyBilateralTrend(String countryCode, String from, String to) {
        Objects.requireNonNull(countryCode, "国家码不能为 null");

        // 先查询中国与指定国家之间的所有事件
        String sql = "SELECT event_date, goldstein, avg_tone, is_coop, source_url, event_id"
                + " FROM events"
                + " WHERE ((actor1_code = 'CHN' AND actor2_code = ?)"
                + "    OR (actor1_code = ? AND actor2_code = 'CHN'))"
                + " AND event_date >= ? AND event_date <= ?"
                + " ORDER BY event_date ASC";

        List<Event> rawEvents = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, countryCode);
            ps.setObject(2, countryCode);
            ps.setObject(3, from);
            ps.setObject(4, to);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Event e = new Event();
                    e.setEventDate(rs.getString("event_date"));
                    e.setGoldstein(rs.getDouble("goldstein"));
                    e.setAvgTone(rs.getDouble("avg_tone"));
                    e.setIsCoop(rs.getInt("is_coop"));
                    String url = rs.getString("source_url");
                    if (!rs.wasNull()) e.setSourceUrl(url);
                    e.setEventId(rs.getLong("event_id"));
                    rawEvents.add(e);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("月度双边趋势查询失败: country=" + countryCode, e);
        }

        // 在 Java 中按 event_date + source_url 去重
        Set<String> seen = new HashSet<>();
        List<Event> deduped = new ArrayList<>();
        for (Event e : rawEvents) {
            String dedupKey = e.getEventDate() + "|"
                + (e.getSourceUrl() != null && !e.getSourceUrl().trim().isEmpty()
                    ? e.getSourceUrl().trim() : String.valueOf(e.getEventId()));
            if (seen.add(dedupKey)) {
                deduped.add(e);
            }
        }

        // 按月份聚合
        Map<String, List<Event>> byMonth = new LinkedHashMap<>();
        for (Event e : deduped) {
            String month = e.getEventDate().substring(0, 7);
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(e);
        }

        List<MonthlyTrend> result = new ArrayList<>();
        for (Map.Entry<String, List<Event>> entry : byMonth.entrySet()) {
            List<Event> monthEvents = entry.getValue();
            MonthlyTrend mt = new MonthlyTrend();
            mt.setMonth(entry.getKey());
            mt.setEventCount(monthEvents.size());
            mt.setAvgGoldstein(monthEvents.stream().mapToDouble(Event::getGoldstein).average().orElse(0));
            mt.setAvgTone(monthEvents.stream().mapToDouble(Event::getAvgTone).average().orElse(0));
            long coopCount = monthEvents.stream().filter(Event::isCooperative).count();
            mt.setCoopRatio(monthEvents.isEmpty() ? 0 : (double) coopCount / monthEvents.size());
            result.add(mt);
        }

        return result;
    }
}
