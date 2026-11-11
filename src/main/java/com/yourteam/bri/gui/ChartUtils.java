package com.yourteam.bri.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Map;

/**
 * 图表绘制工具类 - 使用 JavaFX Canvas API 绘制多种统计图表
 * <p>
 * 提供纯 JavaFX 实现的数据可视化功能，不依赖任何第三方图表库。
 * 支持四种图表类型：
 * <ul>
 *   <li>柱状图（Bar Chart）- 用于 RankingPanel 合作指数排行榜展示</li>
 *   <li>折线图（Line Chart）- 用于 TrendChartPanel 月度趋势展示</li>
 *   <li>雷达图（Radar Chart）- 用于 RadarPanel 多区域多维指标对比</li>
 *   <li>饼图（Pie Chart）- 用于情感分布、事件分类占比展示（预留）</li>
 * </ul>
 * </p>
 * <p>
 * 所有图表均在指定的 Canvas 上绘制，支持自定义标题、颜色、标签等样式。
 * 坐标轴自适应数据范围，自动计算最大值和网格刻度。
 * </p>
 *
 * @see com.yourteam.bri.gui.RankingPanel 柱状图的使用方
 * @see com.yourteam.bri.gui.TrendChartPanel 折线图的使用方
 * @see com.yourteam.bri.gui.RadarPanel 雷达图的使用方
 */
public class ChartUtils {

    // ==================== 柱状图 (合作指数排行榜) ====================

    /**
     * 在画布上绘制垂直柱状图
     * <p>
     * 用于 RankingPanel 中展示一带一路沿线国家的合作指数排行榜。
     * 支持自定义柱体颜色（区域映射）、显示数值标签、自动计算 Y 轴范围。
     * 柱体带圆角和阴影效果，X 轴标签过长时自动截断。
     * </p>
     *
     * @param canvas     目标画布
     * @param labels     X 轴标签列表（国家名称）
     * @param values     Y 轴数值列表（合作指数 0~1）
     * @param barColors  柱体颜色列表（与 labels 长度对应）
     * @param title      图表标题文本
     * @param yAxisLabel Y 轴标签文本
     */
    public static void drawBarChart(Canvas canvas, List<String> labels, List<Double> values,
                                    List<Color> barColors, String title, String yAxisLabel) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);

        // 边距
        double leftMargin = 60;
        double rightMargin = 30;
        double topMargin = 50;
        double bottomMargin = 80;
        double chartW = w - leftMargin - rightMargin;
        double chartH = h - topMargin - bottomMargin;

        // 标题
        gc.setFill(Color.rgb(30, 30, 30));
        gc.setFont(new Font(16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(title, w / 2, 25);

        // Y轴标签（竖排显示）
        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(new Font(11));
        gc.setTextAlign(TextAlignment.CENTER);
        String yLabel = yAxisLabel;
        double yLabelStartY = topMargin + chartH / 2 - (yLabel.length() * 7);
        for (int i = 0; i < yLabel.length(); i++) {
            gc.fillText(String.valueOf(yLabel.charAt(i)), 18, yLabelStartY + i * 14);
        }

        // 计算最大值
        double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        maxVal = Math.ceil(maxVal * 10) / 10.0;
        if (maxVal <= 0) maxVal = 1.0;

        // 绘制Y轴和网格线
        gc.setStroke(Color.rgb(200, 200, 200));
        gc.setLineWidth(0.5);
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            double y = topMargin + chartH - (chartH * i / gridLines);
            gc.strokeLine(leftMargin, y, leftMargin + chartW, y);
            gc.setFill(Color.rgb(100, 100, 100));
            gc.setFont(new Font(10));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.format("%.1f", maxVal * i / gridLines), leftMargin - 5, y + 4);
        }

        // 绘制柱状图
        int n = Math.min(labels.size(), values.size());
        if (n == 0) return;

        double barWidth = Math.min(chartW / n * 0.7, 40);
        double gap = chartW / n;

        for (int i = 0; i < n; i++) {
            double x = leftMargin + gap * i + (gap - barWidth) / 2;
            double barH = (values.get(i) / maxVal) * chartH;
            double y = topMargin + chartH - barH;

            // 柱体
            Color color = barColors != null && i < barColors.size() ? barColors.get(i) : Color.DODGERBLUE;
            gc.setFill(color);
            gc.fillRoundRect(x, y, barWidth, barH, 4, 4);
            gc.setStroke(color.darker());
            gc.setLineWidth(1);
            gc.strokeRoundRect(x, y, barWidth, barH, 4, 4);

            // 数值
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setFont(new Font(10));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("%.2f", values.get(i)), x + barWidth / 2, y - 3);

            // X轴标签（倾斜显示）
            gc.save();
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setFont(new Font(9));
            gc.setTextAlign(TextAlignment.RIGHT);
            double labelX = x + barWidth / 2;
            double labelY = topMargin + chartH + 5;
            gc.translate(labelX, labelY);
            gc.rotate(-35);
            String label = labels.get(i);
            if (label.length() > 8) label = label.substring(0, 7) + "…";
            gc.fillText(label, 0, 0);
            gc.restore();
        }

        // X轴
        gc.setStroke(Color.rgb(100, 100, 100));
        gc.setLineWidth(1.5);
        gc.strokeLine(leftMargin, topMargin + chartH, leftMargin + chartW, topMargin + chartH);

        // X轴标签（“国家”）
        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(new Font(11));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("国家", leftMargin + chartW / 2, h - 8);

        // Y轴
        gc.strokeLine(leftMargin, topMargin, leftMargin, topMargin + chartH);
    }

    // ==================== 折线图 (趋势) ====================

    /**
     * 在画布上绘制多系列折线图
     * <p>
     * 用于 TrendChartPanel 中展示中国与选定国家的月度合作指数趋势。
     * 支持同时绘制多个数据系列（如合作指数、Goldstein 均值、AvgTone 等），
     * 每个系列使用不同颜色和标记点。Y 轴自动适配数据显示范围。
     * 图例行自动换行显示，X 轴标签过长时自动从第5个字符开始截断。
     * </p>
     *
     * @param canvas   目标画布
     * @param xLabels  X 轴标签列表（月份名称，如 "2024-01"）
     * @param seriesList 可变参数，每个参数是一个 List&lt;Double&gt; 数据系列
     *                   最多支持 4 个系列，对应颜色：蓝、橙、绿、紫
     */
    public static void drawLineChart(Canvas canvas, List<String> xLabels,
                                     List<Double>... seriesList) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);

        double leftMargin = 60;
        double rightMargin = 30;
        double topMargin = 40;
        double bottomMargin = 50;
        double chartW = w - leftMargin - rightMargin;
        double chartH = h - topMargin - bottomMargin;

        // 标题
        gc.setFill(Color.rgb(30, 30, 30));
        gc.setFont(new Font(15));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("合作指数月度趋势", w / 2, 22);

        // 找最大值最小值
        double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE;
        for (List<Double> series : seriesList) {
            for (double v : series) {
                if (v < minVal) minVal = v;
                if (v > maxVal) maxVal = v;
            }
        }
        if (minVal == maxVal) { minVal -= 0.1; maxVal += 0.1; }
        double range = maxVal - minVal;
        double padding = range * 0.1;
        minVal -= padding;
        maxVal += padding;

        // 网格和Y轴
        gc.setStroke(Color.rgb(220, 220, 220));
        gc.setLineWidth(0.5);
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            double y = topMargin + chartH - (chartH * i / gridLines);
            gc.strokeLine(leftMargin, y, leftMargin + chartW, y);
            gc.setFill(Color.rgb(100, 100, 100));
            gc.setFont(new Font(10));
            gc.setTextAlign(TextAlignment.RIGHT);
            double val = minVal + range * i / gridLines;
            gc.fillText(String.format("%.2f", val), leftMargin - 5, y + 4);
        }

        // 检查是否有数据
        int n = 0;
        for (List<Double> s : seriesList) {
            if (s != null && !s.isEmpty()) {
                n = Math.max(n, s.size());
            }
        }
        if (n == 0) return;

        Color[] colors = {Color.DODGERBLUE, Color.ORANGERED, Color.LIMEGREEN, Color.PURPLE};
        String[] legendNames = {"合作指数", "Goldstein", "AvgTone", "媒体关注"};

        // 绘制各系列
        for (int si = 0; si < seriesList.length && si < colors.length; si++) {
            List<Double> series = seriesList[si];
            if (series == null || series.isEmpty()) continue;

            gc.setStroke(colors[si]);
            gc.setLineWidth(2);

            double prevX = -1, prevY = -1;
            for (int i = 0; i < series.size(); i++) {
                double x = leftMargin + (double) i / (n - 1) * chartW;
                double y = topMargin + chartH - ((series.get(i) - minVal) / range) * chartH;

                if (prevX >= 0) {
                    gc.strokeLine(prevX, prevY, x, y);
                }

                // 数据点
                gc.setFill(colors[si]);
                gc.fillOval(x - 3, y - 3, 6, 6);

                prevX = x;
                prevY = y;
            }
        }

        // X轴标签
        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(new Font(9));
        gc.setTextAlign(TextAlignment.CENTER);
        if (xLabels != null && !xLabels.isEmpty()) {
            for (int i = 0; i < xLabels.size(); i++) {
                double x = leftMargin + (double) i / (n - 1) * chartW;
                String label = xLabels.get(i);
                if (label.length() > 7) label = label.substring(5);
                gc.fillText(label, x, topMargin + chartH + 15);
            }
        }

        // 图例
        double legendX = leftMargin + 10;
        double legendY = topMargin + 10;
        for (int si = 0; si < seriesList.length && si < colors.length; si++) {
            if (seriesList[si] == null || seriesList[si].isEmpty()) continue;
            gc.setFill(colors[si]);
            gc.fillRect(legendX, legendY, 12, 12);
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setFont(new Font(10));
            gc.fillText(legendNames[si], legendX + 16, legendY + 10);
            legendX += 100;
            if (legendX > leftMargin + chartW - 50) {
                legendX = leftMargin + 10;
                legendY += 20;
            }
        }
    }

    // ==================== 雷达图 ====================

    /**
     * 在画布上绘制多区域雷达图（蜘蛛网图）
     * <p>
     * 用于 RadarPanel 展示五大子区域（东南亚、中亚、中东、欧洲、非洲）的
     * 六维指标对比分析。雷达图包含以下要素：
     * <ul>
     *   <li>同心多边形网格（5 级刻度）</li>
     *   <li>从中心辐射的轴线，每条轴线对应一个指标</li>
     *   <li>轴端显示指标名称标签</li>
     *   <li>每个区域的多边形数据区域（半透明填充 + 描边）</li>
     * </ul>
     * </p>
     *
     * @param canvas       目标画布
     * @param regionData   Map&lt;区域名称, double[] 多维指标数值&gt;（数值 0~1）
     * @param indicators   指标名称数组（如 "合作指数""经济合作"）
     * @param title        图表标题文本
     */
    public static void drawRadarChart(Canvas canvas, Map<String, double[]> regionData,
                                      String[] indicators, String title) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);

        double cx = w / 2;
        double cy = h / 2 - 10;
        double radius = Math.min(w, h) * 0.35;

        // 标题
        gc.setFill(Color.rgb(30, 30, 30));
        gc.setFont(new Font(16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(title, w / 2, 20);

        int indicatorsCount = indicators.length;
        if (indicatorsCount == 0) return;

        // 绘制网格
        gc.setStroke(Color.rgb(200, 200, 200));
        gc.setLineWidth(0.5);
        int levels = 5;
        for (int lv = 1; lv <= levels; lv++) {
            double r = radius * lv / levels;
            gc.strokePolygon(
                getPolygonXs(cx, r, indicatorsCount),
                getPolygonYs(cy, r, indicatorsCount),
                indicatorsCount
            );
        }

        // 绘制轴线
        for (int i = 0; i < indicatorsCount; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / indicatorsCount;
            gc.strokeLine(cx, cy, cx + radius * Math.cos(angle), cy + radius * Math.sin(angle));
        }

        // 指标标签
        gc.setFill(Color.rgb(50, 50, 50));
        gc.setFont(new Font(11));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < indicatorsCount; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / indicatorsCount;
            double labelR = radius + 25;
            double lx = cx + labelR * Math.cos(angle);
            double ly = cy + labelR * Math.sin(angle);
            gc.fillText(indicators[i], lx, ly + 4);
        }

        // 绘制各区域数据
        Color[] colors = {Color.rgb(255, 99, 71, 180), Color.rgb(65, 105, 225, 180),
            Color.rgb(60, 179, 113, 180), Color.rgb(255, 165, 0, 180),
            Color.rgb(138, 43, 226, 180), Color.rgb(255, 20, 147, 180)};
        int colorIdx = 0;

        for (Map.Entry<String, double[]> entry : regionData.entrySet()) {
            String region = entry.getKey();
            double[] values = entry.getValue();

            double[] xs = new double[indicatorsCount];
            double[] ys = new double[indicatorsCount];
            for (int i = 0; i < indicatorsCount && i < values.length; i++) {
                double angle = -Math.PI / 2 + 2 * Math.PI * i / indicatorsCount;
                // 确保值在 0~1 范围内，避免雷达图显示异常
                double v = Math.max(0.01, Math.min(1.0, values[i]));
                double r = radius * v;
                xs[i] = cx + r * Math.cos(angle);
                ys[i] = cy + r * Math.sin(angle);
            }

            Color color = colors[colorIdx % colors.length];
            // 填充
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.15));
            gc.fillPolygon(xs, ys, indicatorsCount);
            // 描边
            gc.setStroke(color);
            gc.setLineWidth(2);
            gc.strokePolygon(xs, ys, indicatorsCount);

            colorIdx++;
        }

        // 图例
        double legendX = 20;
        double legendY = h - 30 - regionData.size() * 18;
        colorIdx = 0;
        for (String region : regionData.keySet()) {
            Color color = colors[colorIdx % colors.length];
            gc.setFill(color);
            gc.fillRect(legendX, legendY, 12, 12);
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setFont(new Font(11));
            gc.fillText(region, legendX + 18, legendY + 10);
            legendY += 18;
            colorIdx++;
        }
    }

    /**
     * 计算正多边形所有顶点的 X 坐标数组（雷达图网格辅助方法）
     * <p>
     * 从正上方（-π/2）开始，按顺时针方向均匀分布 n 个顶点。
     * 用于绘制雷达图的同心网格多边形。
     * </p>
     *
     * @param cx 中心点 X 坐标
     * @param r  外接圆半径
     * @param n  顶点数（等于指标数）
     * @return n 个顶点的 X 坐标数组
     */
    private static double[] getPolygonXs(double cx, double r, int n) {
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = cx + r * Math.cos(-Math.PI / 2 + 2 * Math.PI * i / n);
        }
        return xs;
    }

    /**
     * 计算正多边形所有顶点的 Y 坐标数组（雷达图网格辅助方法）
     * <p>
     * 与 getPolygonXs() 配合使用，共同生成雷达图网格和数据的多边形顶点坐标。
     * </p>
     *
     * @param cy 中心点 Y 坐标
     * @param r  外接圆半径
     * @param n  顶点数（等于指标数）
     * @return n 个顶点的 Y 坐标数组
     */
    private static double[] getPolygonYs(double cy, double r, int n) {
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            ys[i] = cy + r * Math.sin(-Math.PI / 2 + 2 * Math.PI * i / n);
        }
        return ys;
    }

    // ==================== 饼图 ====================

    /**
     * 在画布上绘制饼图
     * <p>
     * 用于展示各类别占比分布，如情感统计（正面/负面/中性）占比、
     * 事件类型占比等。每个扇区标注百分比数值，右侧显示颜色图例。
     * 最多支持绘制 8 个扇区（超出部分不会绘制图例）。
     * </p>
     *
     * @param canvas   目标画布
     * @param labels   扇区标签列表（类别名称）
     * @param values   扇区数值列表（数值之和为总值）
     * @param colors   扇区颜色列表（与 labels 长度对应，不提供则使用默认配色）
     * @param title    图表标题文本
     */
    public static void drawPieChart(Canvas canvas, List<String> labels, List<Double> values,
                                    List<Color> colors, String title) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);

        double cx = w * 0.4;
        double cy = h / 2;
        double radius = Math.min(w, h) * 0.32;

        // 标题
        gc.setFill(Color.rgb(30, 30, 30));
        gc.setFont(new Font(15));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(title, w / 2, 20);

        double total = values.stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return;

        double startAngle = -Math.PI / 2;
        Color[] defaultColors = {Color.DODGERBLUE, Color.ORANGERED, Color.LIMEGREEN,
            Color.GOLD, Color.PURPLE, Color.TEAL, Color.CORAL, Color.CORNFLOWERBLUE};

        for (int i = 0; i < values.size(); i++) {
            double angle = 2 * Math.PI * values.get(i) / total;
            Color c = (colors != null && i < colors.size()) ? colors.get(i) : defaultColors[i % defaultColors.length];
            gc.setFill(c);
            gc.fillArc(cx - radius, cy - radius, radius * 2, radius * 2,
                -Math.toDegrees(startAngle), -Math.toDegrees(angle), javafx.scene.shape.ArcType.ROUND);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2);
            gc.strokeArc(cx - radius, cy - radius, radius * 2, radius * 2,
                -Math.toDegrees(startAngle), -Math.toDegrees(angle), javafx.scene.shape.ArcType.ROUND);

            // 标签
            double midAngle = startAngle + angle / 2;
            double labelR = radius * 0.7;
            double lx = cx + labelR * Math.cos(midAngle);
            double ly = cy + labelR * Math.sin(midAngle);
            gc.setFill(Color.WHITE);
            gc.setFont(new Font(11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("%.0f%%", values.get(i) / total * 100), lx, ly + 4);

            startAngle += angle;
        }

        // 图例
        double legendX = w * 0.7;
        double legendY = h * 0.3;
        for (int i = 0; i < labels.size() && i < 8; i++) {
            Color c = (colors != null && i < colors.size()) ? colors.get(i) : defaultColors[i % defaultColors.length];
            gc.setFill(c);
            gc.fillRect(legendX, legendY, 12, 12);
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setFont(new Font(10));
            gc.fillText(labels.get(i), legendX + 18, legendY + 10);
            legendY += 20;
        }
    }
}
