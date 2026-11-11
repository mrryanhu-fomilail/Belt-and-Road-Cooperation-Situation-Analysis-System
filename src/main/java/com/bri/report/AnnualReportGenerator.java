package com.bri.report;

import com.bri.analysis.AnalysisServiceImpl;
import com.bri.analysis.CountryInfoProvider;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.bri.model.MonthlyTrend;
import com.yourteam.bri.util.BRICountries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 年度报告生成器 — 为指定国家和年份自动生成合作分析报告（HTML 格式）。
 * <p>
 * 报告包含以下内容模块：
 * <ul>
 *   <li>总体概述：国家基本信息、年度合作指数概览</li>
 *   <li>月度趋势分析：合作指数月度变化、Goldstein 趋势、情感语调趋势</li>
 *   <li>事件统计：合作/冲突事件分布、事件类型 TOP 分类</li>
 *   <li>合作指数排名：该国在沿线国家中的排名位置</li>
 *   <li>风险分析：风险等级评估、负面事件占比</li>
 *   <li>综合结论与建议</li>
 * </ul>
 * <p>
 * 支持导出为独立的 HTML 文件（内嵌 CSS 样式，可离线查看），
 * 或返回纯文本 HTML 内容供前端预览。
 * </p>
 */
public class AnnualReportGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 合作/冲突分类标签 */
    private static final String[] COOP_LABELS = {
        "外交合作", "经济合作", "军事合作", "援助合作", "国际法合作", "声明合作"
    };
    private static final String[] CONFLICT_LABELS = {
        "威胁行为", "抗议行为", "军事冲突", "制裁与抵制", "外交冲突", "经济冲突", "暴力冲突"
    };

    // ==================== 报告数据结构 ====================

    /** 年度报告数据模型 */
    public static class ReportData {
        public String countryCode;
        public String countryName;
        public String region;
        public int year;
        public int totalEvents;
        public int coopEvents;
        public int conflictEvents;
        public double coopRatio;
        public double avgGoldstein;
        public double avgTone;
        public double avgIndex;
        public double maxIndex;
        public double minIndex;
        public String peakMonth;
        public String valleyMonth;
        public String trendDirection; // "上升"/"下降"/"平稳"
        public double trendMagnitude;
        public String riskLevel; // "低"/"中"/"高"/"极高"
        public int rankingPosition;
        public int totalCountries;
        public List<String[]> monthlyTrends = new ArrayList<>(); // [month, index, goldstein, tone]
        public List<String[]> eventTypeDistribution = new ArrayList<>(); // [type, count]
        public Map<String, Double> coopSubTypes = new LinkedHashMap<>();
        public Map<String, Double> conflictSubTypes = new LinkedHashMap<>();
        public String analysisText;
        public String suggestionText;
    }

    // ==================== 主生成方法 ====================

    /**
     * 生成指定国家指定年份的年度报告数据
     *
     * @param countryCode 国家三字母代码（如 "PAK"）
     * @param year        年份（如 2024）
     * @return 填充完整的 ReportData 对象，若国家无数据则返回 null
     */
    public ReportData generate(String countryCode, int year) {
        ReportData data = new ReportData();
        data.countryCode = countryCode;
        data.countryName = BRICountries.getCountryName(countryCode);
        data.region = BRICountries.getCountryRegion(countryCode);
        data.year = year;

        try {
            DaoFactory df = DaoFactory.getInstance("bri.db");
            EventDao dao = df.eventDao();

            String from = year + "-01-01";
            String to = year + "-12-31";

            // 1. 获取月度趋势
            List<MonthlyTrend> monthlyTrends = dao.monthlyBilateralTrend(countryCode, from, to);
            if (monthlyTrends == null || monthlyTrends.isEmpty()) {
                // 尝试用更宽松的查询方式
                return null;
            }

            // 2. 查所有事件用于统计
            EventQuery q = new EventQuery.Builder()
                .dateFrom(from).dateTo(to).limit(50000).build();
            List<Event> allEvents = dao.query(q);

            // 过滤与该国相关的事件
            List<Event> countryEvents = allEvents.stream()
                .filter(e -> e != null && e.getEventDate() != null &&
                    (countryCode.equals(e.getActor1Code()) || countryCode.equals(e.getActor2Code())))
                .collect(Collectors.toList());

            // 如果没有双边事件，尝试用 actor1 来查
            if (countryEvents.isEmpty()) {
                countryEvents = allEvents.stream()
                    .filter(e -> e != null && e.getEventDate() != null &&
                        countryCode.equals(e.getActor1Code()))
                    .collect(Collectors.toList());
            }

            // 3. 计算月度数据
            double sumIndex = 0, sumGoldstein = 0, sumTone = 0;
            double maxIdx = -1, minIdx = 2;
            String peakM = "", valleyM = "";
            double firstIdx = -1, lastIdx = -1;

            for (MonthlyTrend mt : monthlyTrends) {
                double gs = mt.getAvgGoldstein();
                double tone = mt.getAvgTone();
                double normGs = Math.max(-1, Math.min(1, gs / 10.0));
                double index = (normGs + 1) / 2.0;
                index = Math.max(0.05, Math.min(1.0, index));

                sumIndex += index;
                sumGoldstein += gs;
                sumTone += tone;

                if (firstIdx < 0) firstIdx = index;
                lastIdx = index;

                String month = mt.getMonth() != null && mt.getMonth().length() >= 7
                    ? mt.getMonth().substring(5) : "";

                data.monthlyTrends.add(new String[]{
                    month != null ? month : "--",
                    String.format("%.4f", index),
                    String.format("%.2f", gs),
                    String.format("%.2f", tone)
                });

                if (index > maxIdx) { maxIdx = index; peakM = month; }
                if (index < minIdx) { minIdx = index; valleyM = month; }
            }

            int n = monthlyTrends.size();
            data.avgIndex = n > 0 ? sumIndex / n : 0;
            data.avgGoldstein = n > 0 ? sumGoldstein / n : 0;
            data.avgTone = n > 0 ? sumTone / n : 0;
            data.maxIndex = maxIdx > 0 ? maxIdx : 0;
            data.minIndex = minIdx < 2 ? minIdx : 0;
            data.peakMonth = peakM;
            data.valleyMonth = valleyM;

            // 趋势方向
            if (firstIdx >= 0 && lastIdx >= 0) {
                double diff = lastIdx - firstIdx;
                data.trendMagnitude = diff;
                if (diff > 0.03) data.trendDirection = "上升";
                else if (diff < -0.03) data.trendDirection = "下降";
                else data.trendDirection = "平稳";
            } else {
                data.trendDirection = "未知";
            }

            // 4. 事件统计
            if (!countryEvents.isEmpty()) {
                data.totalEvents = countryEvents.size();
                long coop = countryEvents.stream().filter(Event::isCooperative).count();
                long conflict = countryEvents.stream().filter(Event::isConflict).count();
                data.coopEvents = (int) coop;
                data.conflictEvents = (int) conflict;
                data.coopRatio = data.totalEvents > 0 ? (double) coop / data.totalEvents * 100 : 0;

                // 事件子类分布
                Map<String, Long> typeCount = countryEvents.stream()
                    .filter(e -> e.getEventRoot() != null)
                    .collect(Collectors.groupingBy(
                        e -> getEventTypeLabel(e.getEventRoot(), e.isCooperative()),
                        Collectors.counting()
                    ));

                for (Map.Entry<String, Long> entry : typeCount.entrySet()) {
                    data.eventTypeDistribution.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
                }

                // 合作子类
                Map<String, Long> coopSub = countryEvents.stream()
                    .filter(Event::isCooperative)
                    .filter(e -> e.getEventRoot() != null)
                    .collect(Collectors.groupingBy(e -> getEventTypeLabel(e.getEventRoot(), true), Collectors.counting()));
                long totalCoop = coopSub.values().stream().mapToLong(Long::longValue).sum();
                for (Map.Entry<String, Long> e : coopSub.entrySet()) {
                    data.coopSubTypes.put(e.getKey(), totalCoop > 0 ? e.getValue() * 100.0 / totalCoop : 0);
                }

                // 冲突子类
                Map<String, Long> conflictSub = countryEvents.stream()
                    .filter(Event::isConflict)
                    .filter(e -> e.getEventRoot() != null)
                    .collect(Collectors.groupingBy(e -> getEventTypeLabel(e.getEventRoot(), false), Collectors.counting()));
                long totalConflict = conflictSub.values().stream().mapToLong(Long::longValue).sum();
                for (Map.Entry<String, Long> e : conflictSub.entrySet()) {
                    data.conflictSubTypes.put(e.getKey(), totalConflict > 0 ? e.getValue() * 100.0 / totalConflict : 0);
                }
            }

            // 5. 风险等级
            double negativeRatio = data.totalEvents > 0
                ? (double) data.conflictEvents / data.totalEvents : 0;
            if (negativeRatio > 0.5 || data.avgGoldstein < -2.0) {
                data.riskLevel = "极高";
            } else if (negativeRatio > 0.3 || data.avgGoldstein < 0) {
                data.riskLevel = "高";
            } else if (negativeRatio > 0.15) {
                data.riskLevel = "中";
            } else {
                data.riskLevel = "低";
            }

            // 6. 排名（估算）
            data.totalCountries = 30;
            data.rankingPosition = estimateRanking(countryCode, year, dao);

            // 7. 文本分析
            data.analysisText = generateAnalysisText(data);
            data.suggestionText = generateSuggestionText(data);

        } catch (Exception e) {
            System.err.println("[AnnualReportGenerator] 生成报告失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        return data;
    }

    // ==================== HTML 报告生成 ====================

    /**
     * 生成完整 HTML 报告字符串
     *
     * @param data 报告数据
     * @return 格式化的 HTML 内容
     */
    public String toHtml(ReportData data) {
        if (data == null) return "<html><body><h2>暂无数据，无法生成报告</h2></body></html>";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(data.countryName).append(" ").append(data.year)
            .append("年合作态势年度报告</title>\n");
        sb.append("<style>\n");
        sb.append(getCssStyles());
        sb.append("</style>\n</head>\n<body>\n");

        // 封面区
        sb.append("<div class=\"cover\">\n");
        sb.append("  <div class=\"cover-badge\">一带一路合作态势分析系统</div>\n");
        sb.append("  <h1 class=\"cover-title\">").append(data.countryName).append("</h1>\n");
        sb.append("  <h2 class=\"cover-subtitle\">").append(data.year).append("年度合作态势报告</h2>\n");
        sb.append("  <div class=\"cover-meta\">\n");
        sb.append("    <span>区域：").append(data.region).append("</span>\n");
        sb.append("    <span>代码：").append(data.countryCode).append("</span>\n");
        sb.append("    <span>生成日期：").append(LocalDate.now().format(FMT)).append("</span>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"cover-index\">\n");
        sb.append("    <div class=\"big-number\">").append(String.format("%.3f", data.avgIndex)).append("</div>\n");
        sb.append("    <div class=\"big-number-label\">年度平均合作指数</div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // 目录
        sb.append("<div class=\"toc\">\n");
        sb.append("  <h2>目录</h2>\n");
        sb.append("  <ul>\n");
        sb.append("    <li><a href=\"#overview\">一、总体概述</a></li>\n");
        sb.append("    <li><a href=\"#trend\">二、月度趋势分析</a></li>\n");
        sb.append("    <li><a href=\"#events\">三、事件统计</a></li>\n");
        sb.append("    <li><a href=\"#ranking\">四、合作指数排名</a></li>\n");
        sb.append("    <li><a href=\"#risk\">五、风险分析</a></li>\n");
        sb.append("    <li><a href=\"#conclusion\">六、综合结论与建议</a></li>\n");
        sb.append("  </ul>\n");
        sb.append("</div>\n");

        // 一、总体概述
        sb.append("<div class=\"section\" id=\"overview\">\n");
        sb.append("  <h2>一、总体概述</h2>\n");
        sb.append("  <div class=\"kpi-row\">\n");
        sb.append(kpiCard("事件总量", String.format("%,d", data.totalEvents), "#3498db"));
        sb.append(kpiCard("合作事件", String.format("%,d", data.coopEvents), "#2ecc71"));
        sb.append(kpiCard("冲突事件", String.format("%,d", data.conflictEvents), "#e74c3c"));
        sb.append(kpiCard("合作占比", String.format("%.1f%%", data.coopRatio), "#9b59b6"));
        sb.append(kpiCard("平均Goldstein", String.format("%.2f", data.avgGoldstein), "#f39c12"));
        sb.append(kpiCard("平均情感语调", String.format("%.2f", data.avgTone), "#1abc9c"));
        sb.append("  </div>\n");
        sb.append("  <div class=\"info-card\">\n");
        sb.append("    <h3>基本信息</h3>\n");
        sb.append("    <table class=\"info-table\">\n");
        sb.append("      <tr><td>国家名称</td><td>").append(data.countryName).append(" (").append(data.countryCode).append(")</td></tr>\n");
        sb.append("      <tr><td>所属区域</td><td>").append(data.region).append("</td></tr>\n");
        sb.append("      <tr><td>报告年份</td><td>").append(data.year).append("年</td></tr>\n");
        sb.append("      <tr><td>数据来源</td><td>GDELT 2.0 事件数据库</td></tr>\n");
        sb.append("      <tr><td>年度平均合作指数</td><td class=\"highlight\">").append(String.format("%.4f", data.avgIndex)).append("</td></tr>\n");
        sb.append("      <tr><td>趋势方向</td><td>").append(data.trendDirection).append(" (").append(String.format("%+.4f", data.trendMagnitude)).append(")</td></tr>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // 二、月度趋势分析
        sb.append("<div class=\"section\" id=\"trend\">\n");
        sb.append("  <h2>二、月度趋势分析</h2>\n");
        sb.append("  <div class=\"trend-summary\">\n");
        sb.append("    <p>").append(data.countryName).append("在").append(data.year).append("年全年的合作指数呈")
            .append(data.trendDirection).append("趋势");
        if (data.trendMagnitude != 0) {
            sb.append("（变化幅度 ").append(String.format("%+.4f", data.trendMagnitude)).append("）");
        }
        sb.append("。</p>\n");
        sb.append("    <p>合作指数最高值出现在").append(data.peakMonth).append("月（")
            .append(String.format("%.4f", data.maxIndex)).append("），");
        sb.append("最低值出现在").append(data.valleyMonth).append("月（")
            .append(String.format("%.4f", data.minIndex)).append("）。</p>\n");
        sb.append("  </div>\n");

        // 月度数据表格
        sb.append("  <table class=\"data-table\">\n");
        sb.append("    <thead><tr><th>月份</th><th>合作指数</th><th>Goldstein 均值</th><th>情感语调</th></tr></thead>\n");
        sb.append("    <tbody>\n");
        for (String[] row : data.monthlyTrends) {
            double idx = Double.parseDouble(row[1]);
            String color = idx > 0.6 ? "#27ae60" : (idx > 0.4 ? "#f39c12" : "#e74c3c");
            sb.append("      <tr><td>").append(row[0]).append("月</td>")
                .append("<td style=\"color:").append(color).append(";font-weight:bold;\">").append(row[1]).append("</td>")
                .append("<td>").append(row[2]).append("</td>")
                .append("<td>").append(row[3]).append("</td></tr>\n");
        }
        sb.append("    </tbody>\n");
        sb.append("  </table>\n");
        sb.append("</div>\n");

        // 三、事件统计
        sb.append("<div class=\"section\" id=\"events\">\n");
        sb.append("  <h2>三、事件统计</h2>\n");
        sb.append("  <div class=\"chart-container\">\n");
        sb.append("    <h3>合作/冲突事件分布</h3>\n");
        sb.append("    <div class=\"bar-chart\">\n");
        double totalForBar = Math.max(data.coopEvents + data.conflictEvents, 1);
        double coopPct = data.coopEvents * 100.0 / totalForBar;
        double conflictPct = data.conflictEvents * 100.0 / totalForBar;
        sb.append("      <div class=\"bar-item\">\n");
        sb.append("        <span class=\"bar-label\">合作事件 (").append(data.coopEvents).append(")</span>\n");
        sb.append("        <div class=\"bar-track\"><div class=\"bar-fill coop\" style=\"width:").append(String.format("%.1f", coopPct)).append("%\"></div></div>\n");
        sb.append("        <span class=\"bar-value\">").append(String.format("%.1f", coopPct)).append("%</span>\n");
        sb.append("      </div>\n");
        sb.append("      <div class=\"bar-item\">\n");
        sb.append("        <span class=\"bar-label\">冲突事件 (").append(data.conflictEvents).append(")</span>\n");
        sb.append("        <div class=\"bar-track\"><div class=\"bar-fill conflict\" style=\"width:").append(String.format("%.1f", conflictPct)).append("%\"></div></div>\n");
        sb.append("        <span class=\"bar-value\">").append(String.format("%.1f", conflictPct)).append("%</span>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");

        // 合作子类分布
        if (!data.coopSubTypes.isEmpty()) {
            sb.append("  <div class=\"chart-container\">\n");
            sb.append("    <h3>合作事件子类分布</h3>\n");
            sb.append("    <table class=\"data-table\">\n");
            sb.append("      <thead><tr><th>子类型</th><th>占比</th></tr></thead>\n");
            sb.append("      <tbody>\n");
            for (Map.Entry<String, Double> e : data.coopSubTypes.entrySet()) {
                sb.append("        <tr><td>").append(e.getKey()).append("</td><td>")
                    .append(String.format("%.1f%%", e.getValue())).append("</td></tr>\n");
            }
            sb.append("      </tbody>\n");
            sb.append("    </table>\n");
            sb.append("  </div>\n");
        }

        // 冲突子类分布
        if (!data.conflictSubTypes.isEmpty()) {
            sb.append("  <div class=\"chart-container\">\n");
            sb.append("    <h3>冲突事件子类分布</h3>\n");
            sb.append("    <table class=\"data-table\">\n");
            sb.append("      <thead><tr><th>子类型</th><th>占比</th></tr></thead>\n");
            sb.append("      <tbody>\n");
            for (Map.Entry<String, Double> e : data.conflictSubTypes.entrySet()) {
                sb.append("        <tr><td>").append(e.getKey()).append("</td><td>")
                    .append(String.format("%.1f%%", e.getValue())).append("</td></tr>\n");
            }
            sb.append("      </tbody>\n");
            sb.append("    </table>\n");
            sb.append("  </div>\n");
        }
        sb.append("</div>\n");

        // 四、合作指数排名
        sb.append("<div class=\"section\" id=\"ranking\">\n");
        sb.append("  <h2>四、合作指数排名</h2>\n");
        sb.append("  <div class=\"info-card\">\n");
        sb.append("    <p>在 ").append(data.countryName).append(" 在一带一路沿线国家中位列")
            .append("<strong>第 ").append(data.rankingPosition).append(" 位</strong>");
        sb.append("（共 ").append(data.totalCountries).append(" 个主要沿线国家）。</p>\n");
        String rankDesc;
        if (data.rankingPosition <= 5) rankDesc = "表现优异，属于深度合作伙伴";
        else if (data.rankingPosition <= 15) rankDesc = "表现良好，合作基础扎实";
        else if (data.rankingPosition <= 25) rankDesc = "合作水平中等，有提升空间";
        else rankDesc = "合作水平有待加强";
        sb.append("    <p>").append(rankDesc).append("。</p>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // 五、风险分析
        sb.append("<div class=\"section\" id=\"risk\">\n");
        sb.append("  <h2>五、风险分析</h2>\n");
        String riskColor = "低".equals(data.riskLevel) ? "#27ae60"
            : "中".equals(data.riskLevel) ? "#f39c12"
            : "高".equals(data.riskLevel) ? "#e67e22" : "#e74c3c";
        sb.append("  <div class=\"risk-badge\" style=\"background-color:").append(riskColor).append(";\">\n");
        sb.append("    风险等级：").append(data.riskLevel).append("\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"info-card\">\n");
        sb.append("    <table class=\"info-table\">\n");
        sb.append("      <tr><td>冲突事件占比</td><td>").append(String.format("%.1f%%", data.totalEvents > 0 ? data.conflictEvents * 100.0 / data.totalEvents : 0)).append("</td></tr>\n");
        sb.append("      <tr><td>平均 Goldstein 值</td><td>").append(String.format("%.2f", data.avgGoldstein)).append("</td></tr>\n");
        sb.append("      <tr><td>平均情感语调</td><td>").append(String.format("%.2f", data.avgTone)).append("</td></tr>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // 六、综合结论与建议
        sb.append("<div class=\"section\" id=\"conclusion\">\n");
        sb.append("  <h2>六、综合结论与建议</h2>\n");
        sb.append("  <div class=\"conclusion-card\">\n");
        sb.append("    <h3>分析结论</h3>\n");
        sb.append("    <p>").append(data.analysisText).append("</p>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"conclusion-card suggestion\">\n");
        sb.append("    <h3>对策建议</h3>\n");
        sb.append("    <p>").append(data.suggestionText).append("</p>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // 页脚
        sb.append("<div class=\"footer\">\n");
        sb.append("  <p>报告由「一带一路合作态势分析系统」自动生成</p>\n");
        sb.append("  <p>数据源：GDELT 2.0 全球事件数据库 | 生成时间：")
            .append(LocalDate.now().format(FMT)).append("</p>\n");
        sb.append("  <p>本报告仅供参考，不构成任何投资或政策建议</p>\n");
        sb.append("</div>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    /**
     * 将报告保存为 HTML 文件
     *
     * @param data   报告数据
     * @param output 输出文件路径
     * @return 是否成功
     */
    public boolean saveToHtmlFile(ReportData data, String output) {
        if (data == null) return false;
        String html = toHtml(data);
        try (PrintWriter pw = new PrintWriter(new FileWriter(output, java.nio.charset.StandardCharsets.UTF_8))) {
            pw.write(html);
            pw.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[AnnualReportGenerator] 保存文件失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 私有辅助方法 ====================

    private String kpiCard(String label, String value, String color) {
        return "<div class=\"kpi-card\">\n"
            + "    <div class=\"kpi-value\" style=\"color:" + color + ";\">" + value + "</div>\n"
            + "    <div class=\"kpi-label\">" + label + "</div>\n"
            + "  </div>\n";
    }

    private String getEventTypeLabel(String rootCode, boolean isCoop) {
        if (rootCode == null) return "其他";
        switch (rootCode) {
            case "01": return "发表声明";
            case "02": return "呼吁行动";
            case "03": return isCoop ? "外交合作" : "外交冲突";
            case "04": return isCoop ? "经济合作" : "经济冲突";
            case "05": return isCoop ? "军事合作" : "军事冲突";
            case "06": return "援助合作";
            case "07": return "国际法合作";
            case "08": return "威胁行为";
            case "09": return "抗议行为";
            case "10": return "军事行动";
            case "11": return "制裁与抵制";
            case "12": return "外交冲突";
            case "13": return "经济冲突";
            case "14": return "暴力冲突";
            case "15": return "军事行动";
            default: return "事件(" + rootCode + ")";
        }
    }

    /** 估计该国在所有一带一路国家中的排名位置 */
    private int estimateRanking(String countryCode, int year, EventDao dao) {
        try {
            // 获取所有一带一路国家的平均合作指数来估算排名
            Map<String, Double> countryScores = new HashMap<>();
            Set<String> briCodes = BRICountries.getCountryCodes();

            for (String code : briCodes) {
                if ("CHN".equals(code)) continue;
                String from = year + "-01-01";
                String to = year + "-12-31";
                List<MonthlyTrend> trends = dao.monthlyBilateralTrend(code, from, to);
                if (trends != null && !trends.isEmpty()) {
                    double sum = 0;
                    for (MonthlyTrend mt : trends) {
                        double gs = mt.getAvgGoldstein();
                        double normGs = Math.max(-1, Math.min(1, gs / 10.0));
                        sum += (normGs + 1) / 2.0;
                    }
                    countryScores.put(code, sum / trends.size());
                }
            }

            // 排序
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(countryScores.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            int pos = 1;
            for (Map.Entry<String, Double> entry : sorted) {
                if (entry.getKey().equals(countryCode)) return pos;
                pos++;
            }
            return pos;
        } catch (Exception e) {
            return 15; // 默认返回中间位置
        }
    }

    private String generateAnalysisText(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(data.year).append("年，").append(data.countryName).append("与中国在一带一路框架下的合作态势");

        if ("上升".equals(data.trendDirection)) {
            sb.append("呈积极向上趋势。");
        } else if ("下降".equals(data.trendDirection)) {
            sb.append("出现一定程度的波动回调。");
        } else {
            sb.append("保持总体平稳。");
        }

        sb.append("全年共记录").append(data.totalEvents).append("起双边事件，");
        sb.append("其中合作类事件").append(data.coopEvents).append("起（占比").append(String.format("%.1f", data.coopRatio)).append("%），");
        sb.append("冲突类事件").append(data.conflictEvents).append("起。");

        sb.append("年度平均合作指数为").append(String.format("%.4f", data.avgIndex));
        sb.append("，最高达到").append(String.format("%.4f", data.maxIndex))
            .append("（").append(data.peakMonth).append("月），");
        sb.append("最低为").append(String.format("%.4f", data.minIndex))
            .append("（").append(data.valleyMonth).append("月）。");

        sb.append("Goldstein量表均值为").append(String.format("%.2f", data.avgGoldstein));
        if (data.avgGoldstein > 2.0) {
            sb.append("，表明双边互动以合作为主");
        } else if (data.avgGoldstein > 0) {
            sb.append("，表明双边互动偏积极");
        } else {
            sb.append("，表明双边互动存在一定紧张因素");
        }
        sb.append("。");

        sb.append("综合评估，").append(data.countryName).append("对华合作风险等级为")
            .append(data.riskLevel).append("。");

        return sb.toString();
    }

    private String generateSuggestionText(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于").append(data.year).append("年全年数据分析，针对").append(data.countryName)
            .append("提出以下合作建议：\n\n");

        if ("低".equals(data.riskLevel) || "中".equals(data.riskLevel)) {
            sb.append("1. 深化现有合作：当前双边合作基础良好，建议在现有框架下进一步拓展")
                .append(data.region).append("区域内的多边合作项目。\n\n");
            sb.append("2. 关注优势领域：重点加强合作指数较高的月份所对应的合作领域，")
                .append("发挥示范效应，带动其他领域的协同发展。\n\n");
            if (data.coopRatio > 50) {
                sb.append("3. 继续保持高频互动：合作事件占比超过50%，建议维持现有的交流机制和合作节奏。\n\n");
            }
        } else {
            sb.append("1. 加强风险管控：当前双边关系中存在一定的不确定因素，")
                .append("建议建立常态化的风险监测和预警机制。\n\n");
            sb.append("2. 推动对话交流：通过增加高层互访和人文交流，增进相互理解，")
                .append("降低误判风险。\n\n");
            sb.append("3. 聚焦低敏领域：优先在基础设施、公共卫生、数字经济等低政治敏感度领域开展合作。\n\n");
        }

        sb.append("4. 利用多边平台：充分发挥一带一路高峰论坛、亚投行等多边机制的作用，")
            .append("推动双边合作在多边框架下稳步发展。");

        return sb.toString();
    }

        private String getCssStyles() {
        return ""
            + "body { font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif; margin: 0; padding: 0; background: #f5f7fa; color: #333; }\n"
            + ".cover { background: linear-gradient(135deg, #1a5276 0%, #2980b9 50%, #1a3a5c 100%); color: white; padding: 60px 40px; text-align: center; }\n"
            + ".cover-badge { display: inline-block; background: rgba(255,255,255,0.15); padding: 6px 18px; border-radius: 20px; font-size: 13px; margin-bottom: 20px; }\n"
            + ".cover-title { font-size: 42px; font-weight: bold; margin: 15px 0; letter-spacing: 4px; }\n"
            + ".cover-subtitle { font-size: 22px; font-weight: normal; opacity: 0.9; margin: 10px 0 20px; }\n"
            + ".cover-meta { font-size: 14px; opacity: 0.7; margin: 15px 0; line-height: 1.8; }\n"
            + ".container { max-width: 960px; margin: 0 auto; padding: 30px 20px; }\n"
            + ".section { background: white; border-radius: 12px; padding: 25px 30px; margin-bottom: 25px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }\n"
            + ".section-title { font-size: 22px; font-weight: bold; color: #1a5276; margin: 0 0 6px 0; padding-bottom: 10px; border-bottom: 3px solid #2980b9; }\n"
            + ".section-desc { font-size: 13px; color: #888; margin: 0 0 18px 0; }\n"
            + ".stat-grid { display: flex; flex-wrap: wrap; gap: 15px; margin: 20px 0; }\n"
            + ".stat-card { flex: 1; min-width: 140px; background: linear-gradient(135deg, #f0f4ff, #e8f0fe); border-radius: 10px; padding: 18px 16px; text-align: center; }\n"
            + ".stat-value { font-size: 28px; font-weight: bold; color: #1a5276; }\n"
            + ".stat-label { font-size: 12px; color: #666; margin-top: 4px; }\n"
            + ".rating-box { display: inline-block; padding: 4px 16px; border-radius: 20px; font-weight: bold; font-size: 16px; }\n"
            + ".rating-A { background: #27ae60; color: white; }\n"
            + ".rating-B { background: #2ecc71; color: white; }\n"
            + ".rating-C { background: #f39c12; color: white; }\n"
            + ".rating-D { background: #e74c3c; color: white; }\n"
            + ".table { width: 100%; border-collapse: collapse; margin: 15px 0; }\n"
            + ".table th { background: #1a5276; color: white; padding: 10px 12px; text-align: left; font-size: 13px; }\n"
            + ".table td { padding: 9px 12px; border-bottom: 1px solid #eee; font-size: 13px; }\n"
            + ".table tr:hover { background: #f5f8ff; }\n"
            + ".highlight-box { background: #fff8e1; border-left: 4px solid #ffc107; padding: 14px 18px; margin: 15px 0; border-radius: 0 8px 8px 0; }\n"
            + ".highlight-box strong { color: #e67e22; }\n"
            + ".footer { text-align: center; padding: 30px; color: #999; font-size: 12px; }\n"
            + ".bar-chart { margin: 20px 0; }\n"
            + ".bar-row { display: flex; align-items: center; margin: 6px 0; gap: 10px; }\n"
            + ".bar-label { width: 80px; font-size: 12px; text-align: right; color: #555; flex-shrink: 0; }\n"
            + ".bar-fill { height: 22px; border-radius: 4px; background: linear-gradient(90deg, #3498db, #2980b9); min-width: 4px; transition: width 0.3s; }\n"
            + ".bar-value { font-size: 11px; color: #888; white-space: nowrap; }\n"
            + ".event-card { background: #f8f9fa; border-radius: 8px; padding: 14px 18px; margin: 10px 0; border-left: 4px solid #3498db; }\n"
            + ".event-card .date { font-size: 12px; color: #2980b9; font-weight: bold; }\n"
            + ".event-card .title { font-size: 14px; font-weight: bold; margin: 4px 0; }\n"
            + ".event-card .desc { font-size: 12px; color: #666; }\n"
            + "pre { background: #f5f5f5; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 12px; }\n";
    }
}