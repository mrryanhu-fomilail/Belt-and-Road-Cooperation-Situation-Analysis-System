package com.bri.output;

import com.bri.analysis.AnalysisServiceImpl;
import com.bri.analysis.CountryInfoProvider;
import com.bri.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON 输出生成器 — 将算法分析结果生成为 needs.docx 要求的 6 个 JSON 文件。
 *
 * <ol>
 *   <li>countries.json — 国家基本信息（30个）</li>
 *   <li>cooperation_index.json — 各国逐月合作指数</li>
 *   <li>cooperation_lines.json — 国家间合作线路</li>
 *   <li>scores.json — 五维度雷达图评分</li>
 *   <li>events.json — 关键事件时间轴</li>
 *   <li>news.json — 新闻数据</li>
 * </ol>
 */
public class JsonOutputGenerator {

    private final ObjectMapper mapper;
    private final AnalysisServiceImpl analysis;
    private final List<Event> allEvents;
    private final Map<String, Country> countryMap; // CAMEO code → Country

    public JsonOutputGenerator(List<Country> countries, List<Event> allEvents) {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.analysis = new AnalysisServiceImpl();
        this.allEvents = allEvents;
        this.countryMap = new HashMap<>();
        for (Country c : countries) countryMap.put(c.getCameoCode(), c);
    }

    // ==================== 文件1: countries.json ====================

    public List<BriCountry> buildCountries() {
        List<BriCountry> list = new ArrayList<>();
        String[] top30 = {
            "CHN","PAK","RUS","KAZ","IDN","MYS","THA","VNM","KHM","LAO",
            "MMR","SGP","IRN","SAU","ARE","TUR","EGY","KEN","ZAF","NGA",
            "ETH","POL","HUN","GRC","SRB","BGR","ROU","DZA","MAR","BGD"
        };
        for (String code : top30) {
            double[] coords = CountryInfoProvider.getLatLon(code);
            if (coords == null) continue;
            String name = CountryInfoProvider.getNameCn(code);
            String nameEn = CountryInfoProvider.getNameEn(code);
            String region = CountryInfoProvider.getRegionCn(code);
            if (name.equals(code)) { // fallback to countryMap
                Country c = countryMap.get(code);
                if (c != null) { name = c.getNameCn(); nameEn = c.getNameEn(); }
            }
            list.add(new BriCountry(code, name, nameEn, region, coords[0], coords[1]));
        }
        return list;
    }

    // ==================== 文件2: cooperation_index.json ====================

    public List<CooperationIndex> buildCooperationIndices(int year) {
        Map<String, List<Event>> groups = new TreeMap<>();
        for (Event e : allEvents) {
            String date = e.getEventDate();
            if (date == null || date.length() < 7) continue;
            String monthKey = date.substring(0, 7);
            addGroup(groups, monthKey, e.getActor1Code(), e);
            addGroup(groups, monthKey, e.getActor2Code(), e);
        }

        List<CooperationIndex> results = new ArrayList<>();
        for (Map.Entry<String, List<Event>> entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("#");
            String countryCode = parts[0];
            String ym = parts[1];
            if (!isBri(countryCode)) continue;

            CooperationIndex ci = new CooperationIndex();
            ci.setCountryCode(countryCode);
            ci.setCountryName(getCn(countryCode));
            ci.setYear(Integer.parseInt(ym.substring(0, 4)));
            ci.setMonth(Integer.parseInt(ym.substring(5, 7)));
            ci.setIndex(analysis.calculateCooperationIndex(entry.getValue()));
            ci.setConflictRatio(analysis.calculateConflictRatio(entry.getValue()));
            ci.setAvgTone(analysis.calculateAverageTone(entry.getValue()));
            ci.setEventCount(entry.getValue().size());
            ci.setTrend(0);
            ci.setTrendDirection("stable");
            results.add(ci);
        }
        results.sort(Comparator.comparing(CooperationIndex::getYear)
                .thenComparing(CooperationIndex::getMonth)
                .thenComparing(CooperationIndex::getCountryCode));
        return results;
    }

    // ==================== 文件3: cooperation_lines.json ====================

    public List<CooperationLine> buildCooperationLines(int year) {
        List<CooperationLine> lines = new ArrayList<>();
        double[] cnCoord = CountryInfoProvider.getLatLon("CHN");
        if (cnCoord == null) return lines;

        Map<String, List<Event>> byTarget = new HashMap<>();
        for (Event e : allEvents) {
            String a1 = e.getActor1Code(), a2 = e.getActor2Code();
            if ("CHN".equals(a1) && !"CHN".equals(a2) && isBri(a2))
                byTarget.computeIfAbsent(a2, k -> new ArrayList<>()).add(e);
            if ("CHN".equals(a2) && !"CHN".equals(a1) && isBri(a1))
                byTarget.computeIfAbsent(a1, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Event>> e : byTarget.entrySet()) {
            String code = e.getKey();
            List<Event> evts = e.getValue();
            double[] tc = CountryInfoProvider.getLatLon(code);
            if (tc == null) continue;

            double gMean = evts.stream().mapToDouble(Event::getGoldstein).average().orElse(0);
            double strength = ((gMean + 10) / 20.0 * 100.0) * 0.6
                    + Math.min(100, evts.size() * 5.0) * 0.4;
            strength = Math.max(10, Math.min(100, strength));

            CooperationLine line = new CooperationLine();
            line.setSourceCode("CHN"); line.setSourceName("中国");
            line.setSourceLat(cnCoord[0]); line.setSourceLon(cnCoord[1]);
            line.setTargetCode(code); line.setTargetName(getCn(code));
            line.setTargetLat(tc[0]); line.setTargetLon(tc[1]);
            line.setStrength(Math.round(strength * 10.0) / 10.0);
            line.setType(detectType(evts));
            line.setYear(year);
            line.setStatus(evts.size() >= 5 ? "active" : "pending");
            line.setGoldsteinMean(Math.round(gMean * 100.0) / 100.0);
            line.setEventCount(evts.size());
            lines.add(line);
        }
        lines.sort((a, b) -> Double.compare(b.getStrength(), a.getStrength()));
        if (lines.size() > 40) lines = lines.subList(0, 40);
        return lines;
    }

    // ==================== 文件4: scores.json ====================

    public List<ScoreItem> buildScores(int year) {
        List<ScoreItem> scores = new ArrayList<>();
        String y = String.valueOf(year);
        for (String code : countryMap.keySet()) {
            List<Event> evts = allEvents.stream()
                    .filter(e -> code.equals(e.getActor1Code()) || code.equals(e.getActor2Code()))
                    .filter(e -> e.getEventDate() != null && e.getEventDate().startsWith(y))
                    .collect(Collectors.toList());
            if (evts.size() < 3) continue;

            double idx = analysis.calculateCooperationIndex(evts);
            Map<String, Double> dims = AnalysisServiceImpl.splitToFiveDimensions(idx, analysis.classifyByCAMEO(evts));
            ScoreItem s = new ScoreItem();
            s.setCountryCode(code); s.setYear(year);
            s.setEconomy(dims.get("economy")); s.setInfrastructure(dims.get("infrastructure"));
            s.setFinance(dims.get("finance")); s.setCulture(dims.get("culture"));
            s.setPolicy(dims.get("policy"));
            scores.add(s);
        }
        return scores;
    }

    // ==================== 文件5: events.json ====================

    public List<KeyEventItem> buildKeyEvents() {
        List<Event> sorted = allEvents.stream()
                .filter(e -> isBri(e.getActor1Code()) || isBri(e.getActor2Code()))
                .sorted((a, b) -> Integer.compare(b.getNumMentions(), a.getNumMentions()))
                .limit(30).collect(Collectors.toList());

        List<KeyEventItem> items = new ArrayList<>();
        int idx = 1;
        for (Event e : sorted) {
            KeyEventItem item = new KeyEventItem();
            item.setId("event-" + String.format("%03d", idx++));
            item.setDate(e.getEventDate());
            String a1 = getCn(e.getActor1Code()), a2 = getCn(e.getActor2Code());
            String act = actionDesc(e);

            if (e.isCooperative()) {
                item.setTitle(a1 + act + a2);
                item.setSummary("合作事件，Goldstein=" + String.format("%.1f", e.getGoldstein()));
            } else if (e.isConflict()) {
                item.setTitle(a1 + "对" + a2 + act);
                item.setSummary("冲突事件，报道" + e.getNumMentions() + "次");
            } else {
                item.setTitle(a1 + "-" + a2 + "事件");
                item.setSummary("中性事件，报道" + e.getNumMentions() + "次");
            }
            item.setDescription("ID=" + e.getEventId() + " | Code=" + e.getEventCode()
                    + " | Goldstein=" + String.format("%.2f", e.getGoldstein())
                    + " | Tone=" + String.format("%.2f", e.getAvgTone())
                    + " | Mentions=" + e.getNumMentions());

            List<String> related = new ArrayList<>();
            if (isBri(e.getActor1Code())) related.add(e.getActor1Code());
            if (isBri(e.getActor2Code())) related.add(e.getActor2Code());
            item.setRelatedCountries(related);
            items.add(item);
            if (items.size() >= 20) break;
        }
        return items;
    }

    // ==================== 文件6: news.json ====================

    public List<NewsItem> buildNews() {
        List<Event> sorted = allEvents.stream()
                .filter(e -> isBri(e.getActor1Code()) || isBri(e.getActor2Code()))
                .filter(e -> e.getNumMentions() > 0)
                .sorted((a, b) -> Integer.compare(b.getNumMentions(), a.getNumMentions()))
                .limit(50).collect(Collectors.toList());

        List<NewsItem> news = new ArrayList<>();
        int idx = 1;
        for (Event e : sorted) {
            NewsItem n = new NewsItem();
            n.setId("news-" + String.format("%03d", idx++));
            n.setDate(e.getEventDate());
            n.setNumMentions(e.getNumMentions());
            n.setSentiment(analysis.classifySentiment(e.getAvgTone()));
            String a1 = getCn(e.getActor1Code()), a2 = getCn(e.getActor2Code());
            n.setTitle(a1 + actionDesc(e) + a2);
            n.setSummary("报道" + e.getNumMentions() + "次，情感" + String.format("%.1f", e.getAvgTone()));
            n.setContent("GDELT#" + e.getEventId() + " | Code=" + e.getEventCode()
                    + " | Goldstein=" + String.format("%.2f", e.getGoldstein())
                    + " | (" + String.format("%.2f", e.getLat()) + "," + String.format("%.2f", e.getLon()) + ")");
            n.setCountryCode(isBri(e.getActor1Code()) ? e.getActor1Code()
                    : isBri(e.getActor2Code()) ? e.getActor2Code() : "UNK");
            news.add(n);
        }
        return news;
    }

    // ==================== 批量写入 ====================

    public void writeAll(String outputDir, int year) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();
        mapper.writeValue(new File(dir, "countries.json"), buildCountries());
        System.out.println("[1/6] countries.json");
        mapper.writeValue(new File(dir, "cooperation_index.json"), buildCooperationIndices(year));
        System.out.println("[2/6] cooperation_index.json");
        mapper.writeValue(new File(dir, "cooperation_lines.json"), buildCooperationLines(year));
        System.out.println("[3/6] cooperation_lines.json");
        mapper.writeValue(new File(dir, "scores.json"), buildScores(year));
        System.out.println("[4/6] scores.json");
        mapper.writeValue(new File(dir, "events.json"), buildKeyEvents());
        System.out.println("[5/6] events.json");
        mapper.writeValue(new File(dir, "news.json"), buildNews());
        System.out.println("[6/6] news.json");
        System.out.println("\n输出完成: " + dir.getAbsolutePath());
    }

    // ---- helpers ----
    private void addGroup(Map<String, List<Event>> g, String mk, String code, Event e) {
        if (code == null || code.isEmpty()) return;
        g.computeIfAbsent(code + "#" + mk, k -> new ArrayList<>()).add(e);
    }

    private boolean isBri(String code) { return code != null && countryMap.containsKey(code); }

    private String getCn(String code) {
        Country c = countryMap.get(code);
        if (c != null) return c.getNameCn();
        String n = CountryInfoProvider.getNameCn(code);
        return n != null && !n.equals(code) ? n : (code != null ? code : "未知");
    }

    private String actionDesc(Event e) {
        if (e.getEventRoot() == null) return "事件";
        try {
            switch (Integer.parseInt(e.getEventRoot())) {
                case 1: return "发表声明";
                case 2: return "呼吁";
                case 3: return "表明意向";
                case 4: return "访问";
                case 5: return "开展合作";
                case 6: return "外交交流";
                case 7: return "提供援助";
                case 8: return "让步";
                case 9: return "调查";
                case 10: return "要求";
                case 11: return "反对";
                case 12: return "拒绝";
                case 13: return "威胁";
                case 14: return "抗议";
                case 15: return "展示武力";
                case 16: return "减少关系";
                case 17: return "强制";
                case 18: return "攻击";
                case 19: return "战斗";
                case 20: return "暴力";
                default: return "事件";
            }
        } catch (NumberFormatException ex) { return "事件"; }
    }

    private String detectType(List<Event> evts) {
        Map<String, Integer> cnt = new TreeMap<>();
        for (Event e : evts) {
            if (e.getEventRoot() != null) cnt.merge(e.getEventRoot(), 1, Integer::sum);
        }
        int eco = cnt.getOrDefault("04", 0) + cnt.getOrDefault("05", 0) + cnt.getOrDefault("06", 0);
        int inf = cnt.getOrDefault("07", 0) + cnt.getOrDefault("17", 0);
        int fin = cnt.getOrDefault("08", 0) + cnt.getOrDefault("02", 0);
        int cul = cnt.getOrDefault("01", 0) + cnt.getOrDefault("03", 0);
        if (eco >= inf && eco >= fin && eco >= cul) return "经贸合作";
        if (inf >= eco && inf >= fin && inf >= cul) return "基础设施";
        if (fin >= eco && fin >= inf && fin >= cul) return "金融合作";
        return "人文交流";
    }
}
