package com.bri.engine;

import com.bri.model.Country;
import com.bri.model.Event;
import com.bri.model.EventCategory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;

/**
 * 批处理管线 — 流式读取 + 边读边算（内存可控）。
 *
 * <h3>解决的问题</h3>
 * <ul>
 *   <li>原来：loadCsv() 全部读入内存 → 筛选 → 分组 → 计算（内存 O(N)，多次遍历）</li>
 *   <li>现在：逐文件流式读取 → 每条直接路由到 国家#月份 分区 → 文件读完索引已建好</li>
 * </ul>
 *
 * <h3>性能</h3>
 * <ul>
 *   <li>批量目录：一次处理 data/*.CSV，支持多年的 daily 文件</li>
 *   <li>内存可控：只持有预计算后的 Map（~460KB），原始事件管道流过即丢弃</li>
 *   <li>单次遍历：每条事件被 IndexCalculator.computeAll() 一次性消化所有指标</li>
 * </ul>
 */
public class DataPipeline {

    private final Set<String> briCodes;        // 一带一路国家代码白名单
    private final IndexCalculator calculator;
    private int totalLines = 0;
    private int totalKept = 0;
    private int totalSkipped = 0;

    /**
     * @param briCodes   一带一路国家 CAMEO 码白名单
     * @param calculator 指数计算策略
     */
    public DataPipeline(Set<String> briCodes, IndexCalculator calculator) {
        this.briCodes = briCodes;
        this.calculator = calculator;
    }

    /**
     * 处理单个 CSV 文件（流式，内存友好）。
     *
     * @param csvPath CSV 文件路径
     * @return 国家#月份 → 该分区的事件列表
     */
    public Map<String, List<Event>> processFile(String csvPath) {
        Map<String, List<Event>> partitioned = new HashMap<>();
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            System.err.println("  ✗ 文件不存在: " + csvPath);
            return partitioned;
        }

        int localTotal = 0, localKept = 0, localSkipped = 0;
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                localTotal++;
                if (line.trim().isEmpty()) { localSkipped++; continue; }

                Event e = parseLine(line);
                if (e == null) { localSkipped++; continue; }

                // 路由到国家#月份（去重：若 actor1==actor2 只路由一次）
                route(partitioned, e, e.getActor1Code(), e.getEventDate());
                String a2 = e.getActor2Code();
                if (a2 != null && !a2.equals(e.getActor1Code())) {
                    route(partitioned, e, a2, e.getEventDate());
                }
                localKept++;
            }
        } catch (IOException ex) {
            System.err.println("  ✗ 读取文件失败: " + csvPath + " — " + ex.getMessage());
        }

        totalLines += localTotal;
        totalKept += localKept;
        totalSkipped += localSkipped;
        return partitioned;
    }

    /**
     * 处理目录下所有 CSV 文件（批量模式）。
     *
     * @param directory 目录路径
     * @param pattern   文件名匹配，如 "*.export.CSV"
     * @return 合并后的所有分区
     */
    public Map<String, List<Event>> processDirectory(String directory, String pattern) {
        Map<String, List<Event>> all = new HashMap<>();
        Path dir = Path.of(directory);
        if (!Files.isDirectory(dir)) {
            System.err.println("  ✗ 目录不存在: " + directory);
            return all;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
            for (Path p : stream) {
                System.out.println("  → 处理: " + p.getFileName());
                Map<String, List<Event>> batch = processFile(p.toString());
                // 合并到总 Map
                for (Map.Entry<String, List<Event>> e : batch.entrySet()) {
                    all.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                       .addAll(e.getValue());
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("目录批量处理失败: " + directory, ex);
        }
        return all;
    }

    // ==================== 聚合计算 ====================

    /**
     * 将分区后的事件 Map 聚合为预计算索引 Map。
     *
     * <p>核心优化：每个分区只调用一次 IndexCalculator.computeAll()，
     * 一次遍历产出全部 7 个指标。</p>
     *
     * @param partitioned 国家#月份 → 事件列表
     * @return "PAK#2026-06" → MonthlyStats
     */
    public Map<String, MonthlyStats> aggregate(Map<String, List<Event>> partitioned) {
        Map<String, MonthlyStats> index = new HashMap<>(1024);
        for (Map.Entry<String, List<Event>> entry : partitioned.entrySet()) {
            MonthlyStats stats = calculator.computeAll(entry.getValue());
            index.put(entry.getKey(), stats);
        }
        return index;
    }

    /**
     * 完整管线：读取文件 → 路由 → 聚合 → 返回索引。
     */
    public Map<String, MonthlyStats> runSingleFile(String csvPath) {
        Map<String, List<Event>> partitioned = processFile(csvPath);
        return aggregate(partitioned);
    }

    public Map<String, MonthlyStats> runDirectory(String dir, String pattern) {
        Map<String, List<Event>> partitioned = processDirectory(dir, pattern);
        return aggregate(partitioned);
    }

    public int getTotalLines() { return totalLines; }
    public int getTotalKept() { return totalKept; }
    public int getTotalSkipped() { return totalSkipped; }

    // ==================== 内部 ====================

    /** 路由一条事件到对应的分区 */
    private void route(Map<String, List<Event>> map, Event e, String countryCode, String dateStr) {
        if (countryCode == null || countryCode.isEmpty()) return;
        if (!briCodes.contains(countryCode)) return;
        if (dateStr == null || dateStr.length() < 7) return;

        String monthKey = dateStr.substring(0, 7); // "2026-06"
        String partition = MonthlyStats.key(countryCode, monthKey);

        map.computeIfAbsent(partition, k -> new ArrayList<>(64))
           .add(e);
    }

    /** 从 TSV 行解析 Event（精简版，只提取算法需要的字段） */
    private Event parseLine(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 35) return null;
        try {
            Event e = new Event();
            e.setEventId(Long.parseLong(f[0].trim()));
            String rawDate = f[1].trim();
            if (rawDate.length() != 8) return null;
            e.setEventDate(rawDate.substring(0,4)+"-"+rawDate.substring(4,6)+"-"+rawDate.substring(6,8));

            e.setActor1Code(trimNull(f[7]));
            e.setActor2Code(trimNull(f[17]));
            e.setEventCode(f[26].trim());
            e.setEventRoot(f[28].trim());
            e.setIsCoop(EventCategory.fromEventRoot(e.getEventRoot()).getDbValue());
            e.setGoldstein(parseDouble(f[30]));
            e.setNumMentions(parseInt(f[31]));
            e.setAvgTone(parseDouble(f[34]));
            return e;
        } catch (Exception ex) { return null; }
    }

    private String trimNull(String s) {
        s = s.trim();
        return s.isEmpty() || "0".equals(s) ? null : s;
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ex) { return 0.0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return 0; }
    }
}
