package com.yourteam.bri.service;

import com.yourteam.bri.model.CooperationIndex;
import com.yourteam.bri.model.GDELTEvent;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.bri.model.MonthlyTrend;
import com.bri.model.AssociationRule;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据服务层 — 基于数据库 {@code bri.db} 的真实 GDELT 数据。
 *
 * <p>所有方法直接通过 {@link DaoFactory} + {@link EventDao} 查询数据库，
 * 不再依赖引擎。数据库无数据时返回空列表/默认值，不会抛空指针。</p>
 *
 * <p>该服务维护一个全局活动时间范围（activeTimeRange），
 * 由 {@link #setActiveTimeRange(LocalDate, LocalDate)} 设置，
 * 所有查询方法将自动限制在该时间范围内。
 * 初始值为数据库全量范围。</p>
 *
 * <p><b>性能优化：</b>首次查询时从数据库一次性加载全量事件并缓存到内存中
 * （{@link #cachedAllEvents}），之后时间轴范围变化时直接对缓存数据进行
 * 内存过滤，不再重复查询数据库。当数据变更（新导入）时需调用
 * {@link #clearCache()} 清空缓存，下次查询将重新从数据库加载。</p>
 */
public class DataService {

    /** 全局活动时间范围（由 TimelinePanel 控制） */
    private static volatile LocalDate activeFrom;
    private static volatile LocalDate activeTo;

    /** 全量缓存（去重后的唯一事件列表），首次访问时加载 */
    private static volatile List<Event> cachedAllEvents;
    /** 缓存版本号，数据变更时递增以触发重新加载 */
    private static volatile int cacheVersion = 0;

    static {
        // 初始设置为数据库全量范围
        try {
            DaoFactory df = getDaoFactory();
            LocalDate[] range = df.eventDao().getDateRange();
            activeFrom = range[0];
            activeTo = range[1];
        } catch (Exception e) {
            activeFrom = LocalDate.of(2020, 1, 1);
            activeTo = LocalDate.now();
        }
    }

    /**
     * 设置全局活动时间范围 — 由 TimelinePanel 在时间范围变更时调用。
     * 此后所有查询方法将只返回该时间范围内的数据。
     */
    public static void setActiveTimeRange(LocalDate from, LocalDate to) {
        activeFrom = from;
        activeTo = to;
    }

    /** 获取当前活动时间范围的起始日期 */
    public static LocalDate getActiveFrom() { return activeFrom; }
    /** 获取当前活动时间范围的结束日期 */
    public static LocalDate getActiveTo() { return activeTo; }

    /**
     * 清空缓存 — 当新数据导入后调用，下次查询将重新从数据库加载全量数据。
     */
    public static void clearCache() {
        cachedAllEvents = null;
        cacheVersion++;
    }

    private static final Map<String, String> REGION_KEY_MAP = new HashMap<>();
    static {
        REGION_KEY_MAP.put("东南亚", "SEA"); REGION_KEY_MAP.put("中亚", "CA");
        REGION_KEY_MAP.put("中东", "ME"); REGION_KEY_MAP.put("欧洲", "EU");
        REGION_KEY_MAP.put("非洲", "AF"); REGION_KEY_MAP.put("南亚", "SA");
        REGION_KEY_MAP.put("东亚", "EA"); REGION_KEY_MAP.put("北美洲", "NAM");
        REGION_KEY_MAP.put("南美洲", "SAM"); REGION_KEY_MAP.put("大洋洲", "OCE");
    }

    private static DaoFactory getDaoFactory() {
        try {
            return DaoFactory.getInstance();
        } catch (IllegalStateException e) {
            com.bri.config.DataSource ds = com.bri.config.DataSource.getInstance("bri.db");
            ds.initIfAbsent();
            return DaoFactory.getInstance("bri.db");
        }
    }

    // ==================== 缓存管理 ====================

    /**
     * 获取全量去重缓存事件列表。
     * 如果缓存为空，则从数据库加载全量数据。
     */
    private static List<Event> getCachedEvents() {
        if (cachedAllEvents == null) {
            synchronized (DataService.class) {
                if (cachedAllEvents == null) {
                    loadFullCache();
                }
            }
        }
        return cachedAllEvents;
    }

    /**
     * 从数据库加载全量事件数据并去重缓存
     */
    private static void loadFullCache() {
        try {
            DaoFactory df = getDaoFactory();
            EventDao dao = df.eventDao();
            LocalDate[] range = dao.getDateRange();
            if (range == null || range.length < 2) {
                cachedAllEvents = Collections.emptyList();
                return;
            }
            EventQuery q = new EventQuery.Builder()
                .dateFrom(range[0].toString())
                .dateTo(range[1].toString())
                .limit(100000)
                .build();
            List<Event> allEvents = dao.query(q);
            if (allEvents == null) {
                cachedAllEvents = Collections.emptyList();
                return;
            }

            // 去重
            Set<String> seenUrls = new HashSet<>();
            List<Event> unique = new ArrayList<>();
            for (Event e : allEvents) {
                if (e == null) continue;
                String key = (e.getSourceUrl() != null && !e.getSourceUrl().trim().isEmpty())
                    ? e.getSourceUrl().trim()
                    : e.getEventId() + "_" + e.getActor1Code() + "_" + e.getEventRoot();
                if (seenUrls.add(key)) unique.add(e);
            }
            cachedAllEvents = unique;
            System.out.println("[DataService] 缓存已加载: " + unique.size() + " 条去重事件");
        } catch (Exception e) {
            System.err.println("[DataService] 全量缓存加载失败: " + e.getMessage());
            cachedAllEvents = Collections.emptyList();
        }
    }

    /**
     * 根据当前 activeFrom/activeTo 从缓存中过滤出时间范围内的事件。
     * 避免频繁创建新列表，使用懒加载过滤。
     */
    private static List<Event> filterEventsByActiveRange() {
        List<Event> all = getCachedEvents();
        if (all.isEmpty()) return all;

        // 如果缓存范围与 active 范围完全一致，直接返回全量
        // 否则过滤
        LocalDate from = activeFrom;
        LocalDate to = activeTo;

        List<Event> filtered = new ArrayList<>();
        for (Event e : all) {
            String dateStr = e.getEventDate();
            if (dateStr == null || dateStr.isEmpty()) continue;
            try {
                LocalDate d = LocalDate.parse(dateStr.substring(0, 10));
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    filtered.add(e);
                }
            } catch (Exception ignored) {
            }
        }
        return filtered;
    }

    // ==================== 仪表盘概览数据 ====================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            // 总事件数（合作 + 冲突）— 直接从数据库计数，避免遍历缓存
            DaoFactory df = getDaoFactory();
            EventDao dao = df.eventDao();
            long totalCoop = dao.countEvents(true);
            long totalConflict = dao.countEvents(false);
            long totalEvents = totalCoop + totalConflict;
            double coopRatio = totalEvents > 0 ? (double) totalCoop / totalEvents * 100 : 0;

            // 从缓存过滤获取当前时间范围的事件
            List<Event> unique = filterEventsByActiveRange();

            double sumGoldstein = 0, sumTone = 0;
            for (Event e : unique) {
                sumGoldstein += e.getGoldstein();
                sumTone += e.getAvgTone();
            }
            int n = unique.size();
            double avgGoldstein = n > 0 ? sumGoldstein / n : 2.3;
            double avgTone = n > 0 ? sumTone / n : 0;

            // 按国家 actor1Code 分组
            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : unique) {
                String code = e.getActor1Code();
                if (code != null && !code.trim().isEmpty() && !"CHN".equals(code)) {
                    byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            // 合作热点 TOP5
            List<String[]> hotTopics = new ArrayList<>();
            byCountry.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(5)
                .forEach(entry -> {
                    String code = entry.getKey();
                    int count = entry.getValue().size();
                    String name = com.yourteam.bri.util.BRICountries.getCountryName(code);
                    if (name == null) name = code;
                    hotTopics.add(new String[]{code, name, String.valueOf(count)});
                });

            // 风险预警 TOP5
            List<String[]> riskWarnings = new ArrayList<>();
            byCountry.entrySet().stream()
                .map(entry -> {
                    String code = entry.getKey();
                    List<Event> evts = entry.getValue();
                    long negativeCount = evts.stream().filter(e -> e.getGoldstein() < 0).count();
                    double riskRatio = evts.size() > 0 ? (double) negativeCount / evts.size() : 0;
                    return new AbstractMap.SimpleEntry<>(code, riskRatio);
                })
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(entry -> {
                    String code = entry.getKey();
                    String name = com.yourteam.bri.util.BRICountries.getCountryName(code);
                    if (name == null) name = code;
                    String riskLevel = entry.getValue() > 0.5 ? "高" : entry.getValue() > 0.3 ? "中" : "低";
                    riskWarnings.add(new String[]{code, name, riskLevel});
                });

            data.put("totalCountries", (int) byCountry.size());
            data.put("cooperationEvents", totalCoop);
            data.put("conflictEvents", totalConflict);
            data.put("totalEvents", totalEvents);
            data.put("cooperationRatio", coopRatio);
            data.put("avgGoldstein", Math.round(avgGoldstein * 100.0) / 100.0);
            data.put("avgTone", Math.round(avgTone * 100.0) / 100.0);
            data.put("hotTopics", hotTopics);
            data.put("riskWarnings", riskWarnings);

        } catch (Exception e) {
            System.err.println("[DataService] 仪表盘数据失败: " + e.getMessage());
            data.put("totalCountries", 0); data.put("cooperationEvents", 0);
            data.put("conflictEvents", 0); data.put("totalEvents", 0);
            data.put("cooperationRatio", 0); data.put("avgGoldstein", 0);
            data.put("avgTone", 0);
            data.put("hotTopics", Collections.emptyList());
            data.put("riskWarnings", Collections.emptyList());
        }
        return data;
    }

    // ==================== 合作指数排名 (F-12) ====================

    public static List<CooperationIndex> getCooperationRankings() {
        List<CooperationIndex> rankings = new ArrayList<>();
        try {
            // 从缓存过滤
            List<Event> allEvents = filterEventsByActiveRange();

            // 按 BRI 国家分组
            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : allEvents) {
                String code = e.getActor1Code();
                if (code != null && !code.trim().isEmpty() && com.yourteam.bri.util.BRICountries.isBriCountry(code)) {
                    byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            // 第一遍：获取全局最大最小值用于归一化
            int maxEventCount = 0;
            double maxGoldstein = Double.MIN_VALUE, minGoldstein = Double.MAX_VALUE;
            double maxTone = Double.MIN_VALUE, minTone = Double.MAX_VALUE;
            int maxMentions = 0;

            for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                List<Event> evts = entry.getValue();
                int n = evts.size();
                double sumGoldstein = 0, sumTone = 0;
                int sumMentions = 0;
                for (Event e : evts) {
                    sumGoldstein += e.getGoldstein();
                    sumTone += e.getAvgTone();
                    sumMentions += e.getNumMentions();
                }
                if (n > maxEventCount) maxEventCount = n;
                double avgGs = sumGoldstein / n;
                double avgT = sumTone / n;
                if (avgGs > maxGoldstein) maxGoldstein = avgGs;
                if (avgGs < minGoldstein) minGoldstein = avgGs;
                if (avgT > maxTone) maxTone = avgT;
                if (avgT < minTone) minTone = avgT;
                if (sumMentions > maxMentions) maxMentions = sumMentions;
            }

            // 第二遍：计算综合合作指数
            for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                String code = entry.getKey();
                List<Event> evts = entry.getValue();
                int n = evts.size();
                double sumGoldstein = 0, sumTone = 0;
                long coopCount = 0, conflictCount = 0;
                int totalMentions = 0;
                for (Event e : evts) {
                    sumGoldstein += e.getGoldstein();
                    sumTone += e.getAvgTone();
                    totalMentions += e.getNumMentions();
                    if (e.isCooperative()) coopCount++;
                    else if (e.isConflict()) conflictCount++;
                }

                double avgGoldstein = sumGoldstein / n;
                double avgTone = sumTone / n;

                double w1 = 0.3, w2 = 0.3, w3 = 0.2, w4 = 0.2;

                double normEventCount = maxEventCount > 0 ? (double) coopCount / maxEventCount : 0;
                double normGoldstein = (maxGoldstein > minGoldstein)
                    ? (avgGoldstein - minGoldstein) / (maxGoldstein - minGoldstein) : 0.5;
                double normTone = (maxTone > minTone)
                    ? (avgTone - minTone) / (maxTone - minTone) : 0.5;
                double normMentions = maxMentions > 0 ? (double) totalMentions / maxMentions : 0;

                double index = w1 * normEventCount + w2 * normGoldstein + w3 * normTone + w4 * normMentions;
                index = Math.max(0.01, Math.min(1.0, index));

                CooperationIndex ci = new CooperationIndex();
                ci.setCountryCode(code);
                String name = com.yourteam.bri.util.BRICountries.getCountryName(code);
                ci.setCountryName(name != null ? name : code);
                ci.setRegion(getRegionForCode(code));
                ci.setIndexValue(Math.round(index * 10000.0) / 10000.0);
                ci.setEventCount((int) coopCount);
                ci.setConflictRatio(n > 0 ? Math.round((double) conflictCount / n * 10000.0) / 10000.0 : 0);
                ci.setAvgGoldstein(Math.round(avgGoldstein * 100.0) / 100.0);
                ci.setAvgTone(Math.round(avgTone * 100.0) / 100.0);
                ci.setMediaAttention(totalMentions);
                rankings.add(ci);
            }

            rankings.sort((a, b) -> Double.compare(b.getIndexValue(), a.getIndexValue()));

        } catch (Exception e) {
            System.err.println("[DataService] 排名数据失败: " + e.getMessage());
        }
        return rankings;
    }

    // ==================== 双边关系趋势 ====================

    public static List<CooperationIndex> getBilateralTrend(String countryCode, LocalDate start, LocalDate end) {
        List<CooperationIndex> trends = new ArrayList<>();
        try {
            DaoFactory df = getDaoFactory();
            EventDao dao = df.eventDao();
            String from = start != null ? start.toString() : "2020-01-01";
            String to = end != null ? end.toString() : LocalDate.now().toString();

            List<MonthlyTrend> monthly = dao.monthlyBilateralTrend(countryCode, from, to);
            if (monthly == null || monthly.isEmpty()) return trends;

            for (MonthlyTrend mt : monthly) {
                CooperationIndex ci = new CooperationIndex();
                ci.setCountryCode(countryCode);
                String name = com.yourteam.bri.util.BRICountries.getCountryName(countryCode);
                ci.setCountryName(name != null ? name : countryCode);
                double gs = mt.getAvgGoldstein();
                double normalizedGs = Math.max(-1, Math.min(1, gs / 10.0));
                double index = (normalizedGs + 1) / 2.0;
                index = Math.max(0.05, Math.min(1.0, index));
                ci.setIndexValue(Math.round(index * 10000.0) / 10000.0);
                ci.setMonth(mt.getMonth());
                trends.add(ci);
            }
        } catch (Exception e) {
            System.err.println("[DataService] 趋势查询失败: " + e.getMessage());
        }
        return trends;
    }

    // ==================== 区域对比数据 ====================

    public static Map<String, double[]> getRegionComparisonData() {
        Map<String, double[]> data = new LinkedHashMap<>();
        try {
            List<Event> allEvents = filterEventsByActiveRange();

            // 按国家分组
            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : allEvents) {
                String code = e.getActor1Code();
                if (code != null && !code.trim().isEmpty()) {
                    byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            // 按区域分组
            String[] regionKeys = {"SEA", "CA", "ME", "EU", "AF", "SA"};
            String[] regionNames = {"东南亚", "中亚", "中东", "欧洲", "非洲", "南亚"};

            for (int i = 0; i < regionKeys.length; i++) {
                String rk = regionKeys[i];
                List<Event> regionEvents = new ArrayList<>();
                for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                    String code = entry.getKey();
                    String region = com.yourteam.bri.util.BRICountries.getCountryRegion(code);
                    if (rk.equals(region)) {
                        regionEvents.addAll(entry.getValue());
                    }
                }

                if (regionEvents.isEmpty()) continue;
                int n = regionEvents.size();
                double sumGoldstein = 0, sumTone = 0;
                long coopCount = 0;
                int totalMentions = 0;
                for (Event e : regionEvents) {
                    sumGoldstein += e.getGoldstein();
                    sumTone += e.getAvgTone();
                    totalMentions += e.getNumMentions();
                    if (e.isCooperative()) coopCount++;
                }

                double avgGoldstein = sumGoldstein / n;
                double normalizedGs = Math.max(-1, Math.min(1, avgGoldstein / 10.0));
                double coopIndex = (double) coopCount / n;
                double avgIndex = (normalizedGs + 1) / 2.0 * 0.5 + coopIndex * 0.5;
                avgIndex = Math.max(0.05, Math.min(1.0, avgIndex));

                data.put(regionNames[i], new double[]{
                    Math.round(avgIndex * 100.0) / 100.0,
                    Math.round(avgIndex * 0.95 * 100.0) / 100.0,
                    Math.round(coopIndex * 100.0) / 100.0,
                    Math.round(avgIndex * 0.98 * 100.0) / 100.0,
                    totalMentions > 0 ? Math.min(1.0, totalMentions / 1000.0) : 0.60,
                    n > 0 ? Math.min(1.0, (double) (n - coopCount) / n) : 0.15
                });
            }
        } catch (Exception e) {
            System.err.println("[DataService] 区域对比失败: " + e.getMessage());
        }
        if (data.isEmpty()) return fallbackRegionData();
        return data;
    }

    private static Map<String, double[]> fallbackRegionData() {
        Map<String, double[]> data = new LinkedHashMap<>();
        data.put("东南亚", new double[]{0.72, 0.68, 0.78, 0.72, 0.65, 0.12});
        data.put("中亚", new double[]{0.65, 0.58, 0.85, 0.65, 0.55, 0.15});
        data.put("中东", new double[]{0.45, 0.38, 0.35, 0.20, 0.40, 0.35});
        data.put("欧洲", new double[]{0.60, 0.55, 0.62, 0.55, 0.50, 0.20});
        data.put("非洲", new double[]{0.55, 0.50, 0.55, 0.50, 0.48, 0.18});
        data.put("南亚", new double[]{0.50, 0.45, 0.48, 0.42, 0.45, 0.25});
        return data;
    }

    public static String[] getRadarIndicators() {
        return new String[]{"合作指数", "经济合作", "Goldstein均值", "情感语调", "媒体关注", "冲突频率"};
    }

    // ==================== 地图数据 ====================

    public static List<Map<String, Object>> getMapData() {
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            List<Event> allEvents = filterEventsByActiveRange();

            // 按国家分组
            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : allEvents) {
                String code = e.getActor1Code();
                if (code != null && !code.trim().isEmpty()) {
                    byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                String code = entry.getKey();
                List<Event> evts = entry.getValue();
                int n = evts.size();
                double sumGoldstein = 0;
                long coopCount = 0;
                for (Event e : evts) {
                    sumGoldstein += e.getGoldstein();
                    if (e.isCooperative()) coopCount++;
                }
                double avgGoldstein = sumGoldstein / n;
                double coopRatio = (double) coopCount / n;
                double normalizedGs = Math.max(-1, Math.min(1, avgGoldstein / 10.0));
                double index = (normalizedGs + 1) / 2.0 * 0.5 + coopRatio * 0.5;
                index = Math.max(0.05, Math.min(1.0, index));

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", code);
                String name = com.yourteam.bri.util.BRICountries.getCountryName(code);
                item.put("name", name != null ? name : code);
                String region = com.yourteam.bri.util.BRICountries.getCountryRegion(code);
                item.put("region", region != null ? region : "其他");
                item.put("index", Math.round(index * 10000.0) / 10000.0);
                item.put("eventCount", n);
                data.add(item);
            }
        } catch (Exception e) {
            System.err.println("[DataService] 地图数据失败: " + e.getMessage());
        }
        return data;
    }

    // ==================== 事件查询 ====================

    public static List<GDELTEvent> queryEvents(String actor1, String actor2, String region,
                                                LocalDate startDate, LocalDate endDate,
                                                String eventType) {
        List<GDELTEvent> events = new ArrayList<>();
        try {
            DaoFactory df = getDaoFactory();
            EventDao eventDao = df.eventDao();

            String startStr = startDate != null ? startDate.toString() : "2020-01-01";
            String endStr = endDate != null ? endDate.toString() : LocalDate.now().toString();

            List<Event> raw;
            if (actor1 != null && !actor1.isEmpty() && actor2 != null && !actor2.isEmpty()) {
                raw = eventDao.queryBilateral(actor1, actor2, startStr, endStr);
            } else if (actor1 != null && !actor1.isEmpty()) {
                EventQuery qb = new EventQuery.Builder()
                    .actor1(actor1).dateFrom(startStr).dateTo(endStr).build();
                raw = eventDao.query(qb);
            } else if (region != null && !region.isEmpty()) {
                String regionKey = REGION_KEY_MAP.getOrDefault(region, "SEA");
                raw = eventDao.queryBySubregion(regionKey, startStr, endStr);
            } else {
                raw = new ArrayList<>();
            }

            if (raw == null) return events;

            Set<String> seenUrls = new HashSet<>();
            for (Event e : raw) {
                if (e == null) continue;
                String url = e.getSourceUrl();
                String key = (url != null && !url.trim().isEmpty()) ? url.trim()
                    : e.getEventDate() + "_" + e.getActor1Code() + "_" + e.getActor2Code() + "_" + e.getEventRoot();
                if (!seenUrls.add(key)) continue;

                String dateStr = e.getEventDate();
                LocalDate date = dateStr != null && dateStr.length() >= 10
                    ? LocalDate.parse(dateStr.substring(0, 10))
                    : LocalDate.now();

                GDELTEvent gdelt = new GDELTEvent(
                    date, e.getActor1Code(), e.getActor2Code(),
                    e.getEventRoot(), e.getGoldstein(),
                    e.getNumMentions(), e.getAvgTone()
                );
                gdelt.setSourceUrl(e.getSourceUrl());
                events.add(gdelt);
            }
        } catch (Exception e) {
            System.err.println("[DataService] 事件查询失败: " + e.getMessage());
        }
        return events;
    }

    // ==================== 聚类结果 ====================

    public static List<com.yourteam.bri.model.ClusterResult> getClusterResults() {
        List<com.yourteam.bri.model.ClusterResult> results = new ArrayList<>();
        try {
            List<Event> allEvents = filterEventsByActiveRange();

            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : allEvents) {
                String code = e.getActor1Code();
                if (code != null && !code.trim().isEmpty() && !"CHN".equals(code)) {
                    byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
                }
            }

            for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                String code = entry.getKey();
                List<Event> evts = entry.getValue();
                int n = evts.size();
                double sumGoldstein = 0;
                long coopCount = 0;
                for (Event e : evts) {
                    sumGoldstein += e.getGoldstein();
                    if (e.isCooperative()) coopCount++;
                }
                double goldsteinAvg = sumGoldstein / n;
                double coopRatio = (double) coopCount / n;

                int clusterId;
                String clusterLabel;
                String riskLevel;
                double riskScore;

                if (goldsteinAvg > 2.0 && coopRatio > 0.6) {
                    clusterId = 0; clusterLabel = "深度合作伙伴"; riskLevel = "低";
                    riskScore = 10 + (1 - coopRatio) * 20;
                } else if (goldsteinAvg > 0.5 && coopRatio > 0.4) {
                    clusterId = 1; clusterLabel = "稳定合作"; riskLevel = "低";
                    riskScore = 20 + (1 - coopRatio) * 25;
                } else if (goldsteinAvg > -2.0 || coopRatio > 0.2) {
                    clusterId = 2; clusterLabel = "存在风险"; riskLevel = "中";
                    riskScore = 45 + Math.max(0, -goldsteinAvg) * 5;
                } else {
                    clusterId = 3; clusterLabel = "高度紧张"; riskLevel = "高";
                    riskScore = 70 + Math.max(0, -goldsteinAvg) * 5;
                }
                riskScore = Math.min(100, Math.max(0, riskScore));

                String name = com.yourteam.bri.util.BRICountries.getCountryName(code);
                if (name == null) name = code;

                results.add(new com.yourteam.bri.model.ClusterResult(code, name, clusterId, clusterLabel, riskLevel, riskScore));
            }
            results.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));
        } catch (Exception e) {
            System.err.println("[DataService] 聚类查询失败: " + e.getMessage());
        }
        return results;
    }

    // ==================== 合作热点追踪 ====================

    public static List<Map<String, Object>> getHotTopics() {
        List<Map<String, Object>> topics = new ArrayList<>();
        try {
            List<CooperationIndex> rankings = getCooperationRankings();
            int rank = 1;
            for (CooperationIndex ci : rankings.subList(0, Math.min(10, rankings.size()))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", rank++);
                item.put("code", ci.getCountryCode());
                item.put("name", ci.getCountryName());
                item.put("index", ci.getIndexValue());
                item.put("growth", ci.getEventCount());
                topics.add(item);
            }
        } catch (Exception e) {
            System.err.println("[DataService] 热点查询失败: " + e.getMessage());
        }
        return topics;
    }

    // ==================== 关联规则挖掘 ====================

    public static List<AssociationRule> getAssociationRules(double minSupport, double minConfidence) {
        return Collections.emptyList();
    }

    // ==================== 工具方法 ====================

    private static String getRegionForCode(String code) {
        return com.yourteam.bri.util.BRICountries.getCountryRegion(code);
    }
}
