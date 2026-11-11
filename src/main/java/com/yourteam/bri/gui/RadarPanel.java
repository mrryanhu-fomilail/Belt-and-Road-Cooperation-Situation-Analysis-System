package com.yourteam.bri.gui;

import com.bri.config.DataSource;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.yourteam.bri.util.BRICountries;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 区域对比雷达图面板 - 子区域多指标雷达图对比
 * <p>
 * 从数据库 bri.db 读取真实事件数据，按 BRICountries 中的区域分组，
 * 计算各区域的六维指标，以雷达图展示多区域对比。
 * </p>
 */
public class RadarPanel extends VBox {

    private Canvas radarCanvas;

    /** 子区域名称列表（从 BRICountries 动态获取） */
    private List<String> regionNames;

    /** 被用户隐藏的区域（不显示在雷达图中） */
    private final Set<String> hiddenRegions = new HashSet<>();

    /** 区域原始（未归一化）数据，用于展示具体数值 */
    private Map<String, double[]> rawRegionValues = new LinkedHashMap<>();

    /** 信息标签：展示点击区域的详细数值 */
    private Label infoLabel;

    /** 六维指标名称 */
    private static final String[] INDICATORS = {"合作事件占比", "Goldstein均值", "情感语调",
                                                  "冲突事件占比", "事件总量占比", "合作丰富度"};

    /** 区域对应颜色（支持最多10个区域） */
    private static final Color[] REGION_COLORS = {
        Color.web("#FF6347"),   // 东南亚 - 番茄红
        Color.web("#4169E1"),   // 中亚 - 皇家蓝
        Color.web("#3CB371"),   // 中东 - 海洋绿
        Color.web("#FFA500"),   // 欧洲 - 橙色
        Color.web("#8B2BE2"),   // 非洲 - 蓝紫色
        Color.web("#FF1493"),   // 南亚 - 深粉红
        Color.web("#00CED1"),   // 北美洲 - 暗宝石绿
        Color.web("#DC143C"),   // 南美洲 - 深红
        Color.web("#FFD700"),   // 大洋洲 - 金色
        Color.web("#2F4F4F")    // 东亚 - 暗灰
    };

    /** 区域半透明填充色 */
    private static final Color[] REGION_FILLS = {
        Color.web("#FF6347", 0.25),
        Color.web("#4169E1", 0.25),
        Color.web("#3CB371", 0.25),
        Color.web("#FFA500", 0.25),
        Color.web("#8B2BE2", 0.25),
        Color.web("#FF1493", 0.25),
        Color.web("#00CED1", 0.25),
        Color.web("#DC143C", 0.25),
        Color.web("#FFD700", 0.25),
        Color.web("#2F4F4F", 0.25)
    };

    public RadarPanel() {
        // 从 BRICountries 获取所有区域
        regionNames = BRICountries.getRegions().stream()
            .filter(r -> !"未知".equals(r))
            .collect(Collectors.toList());

        setPadding(new Insets(12));
        setSpacing(8);
        setStyle("-fx-background-color: #f5f7fa;");

        Label titleLabel = new Label("\uD83D\uDCE1 区域对比雷达图");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        // 右上角刷新按钮
        Button refreshBtn = new Button("\uD83D\uDD04 刷新");
        refreshBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #3498db; -fx-text-fill: white; " +
            "-fx-padding: 6px 16px; -fx-background-radius: 5; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> drawRadar());

        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(titleLabel, spacer, refreshBtn);

        String regionsDesc = String.join(" / ", regionNames);
        Label descLabel = new Label("子区域（" + regionsDesc + "）多指标对比分析");
        descLabel.setFont(Font.font("Microsoft YaHei", 13));
        descLabel.setTextFill(Color.GRAY);

        // 缩小画布尺寸，给底部时间轴留空间
        radarCanvas = new Canvas(580, 380);
        radarCanvas.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        StackPane canvasContainer = new StackPane(radarCanvas);
        canvasContainer.setPadding(new Insets(10));
        canvasContainer.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        // 右侧图例面板
        VBox legendPanel = new VBox(10);
        legendPanel.setPadding(new Insets(12));
        legendPanel.setPrefWidth(180);
        legendPanel.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        Label legendTitle = new Label("区域图例（点击取消勾选可隐藏）");
        legendTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        legendTitle.setTextFill(Color.web("#1e1e3c"));
        legendPanel.getChildren().add(legendTitle);

        for (int i = 0; i < regionNames.size(); i++) {
            String regionName = regionNames.get(i);
            CheckBox cb = new CheckBox(regionName);
            cb.setFont(Font.font("Microsoft YaHei", 11));
            cb.setTextFill(REGION_COLORS[i % REGION_COLORS.length]);
            cb.setSelected(true);
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    hiddenRegions.remove(regionName);
                } else {
                    hiddenRegions.add(regionName);
                }
                drawRadar();
            });
            legendPanel.getChildren().add(cb);
        }

        // 指标说明
        VBox indPanel = new VBox(6);
        indPanel.setPadding(new Insets(10, 0, 0, 0));

        Label indTitle = new Label("指标说明");
        indTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        indTitle.setTextFill(Color.web("#1e1e3c"));

        Color[] indColors = {Color.web("#FF6347"), Color.web("#4169E1"), Color.web("#3CB371"),
                             Color.web("#FFA500"), Color.web("#8B2BE2"), Color.web("#FF1493")};
        for (int i = 0; i < INDICATORS.length; i++) {
            HBox item = new HBox(6);
            item.setAlignment(Pos.CENTER_LEFT);
            Label dot = new Label((i + 1) + ".");
            dot.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 11));
            dot.setTextFill(indColors[i]);
            Label nameLabel = new Label(INDICATORS[i]);
            nameLabel.setFont(Font.font("Microsoft YaHei", 11));
            nameLabel.setTextFill(Color.web("#555"));
            item.getChildren().addAll(dot, nameLabel);
            indPanel.getChildren().add(item);
        }

        legendPanel.getChildren().addAll(indPanel);

        // 主内容：左侧画布 + 右侧图例
        HBox mainContent = new HBox(12);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.getChildren().addAll(canvasContainer, legendPanel);

        // 信息标签：展示点击区域的详细数据
        infoLabel = new Label("点击雷达图上的区域可查看具体数值");
        infoLabel.setFont(Font.font("Microsoft YaHei", 12));
        infoLabel.setTextFill(Color.web("#7f8c8d"));
        infoLabel.setPadding(new Insets(8, 14, 8, 14));
        infoLabel.setStyle("-fx-background-color: white; -fx-background-radius: 6; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        infoLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(titleBar, descLabel, mainContent, infoLabel);
        drawRadar();
    }

    private void drawRadar() {
        Map<String, double[]> regionData = computeRegionData();
        GraphicsContext gc = radarCanvas.getGraphicsContext2D();
        double w = radarCanvas.getWidth();
        double h = radarCanvas.getHeight();
        gc.clearRect(0, 0, w, h);

        if (regionData.isEmpty()) {
            gc.setFill(Color.GRAY);
            gc.setFont(new Font("Microsoft YaHei", 16));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("暂无数据，请先导入 GDELT 数据", w / 2, h / 2);
            return;
        }

        double cx = w / 2 - 20;
        double cy = h / 2 - 5;
        double maxR = Math.min(cx - 35, cy - 30);
        int n = INDICATORS.length;

        double[] angles = new double[n];
        for (int i = 0; i < n; i++) {
            angles[i] = -Math.PI / 2 + i * 2 * Math.PI / n;
        }

        // 背景网格
        gc.setStroke(Color.web("#e0e0e0"));
        gc.setLineWidth(1);
        for (int level = 1; level <= 5; level++) {
            double r = maxR * level / 5;
            double[] xPts = new double[n];
            double[] yPts = new double[n];
            for (int i = 0; i < n; i++) {
                xPts[i] = cx + r * Math.cos(angles[i]);
                yPts[i] = cy + r * Math.sin(angles[i]);
            }
            gc.strokePolygon(xPts, yPts, n);
        }

        // 放射轴线
        gc.setStroke(Color.web("#c0c0c0"));
        gc.setLineWidth(1);
        for (int i = 0; i < n; i++) {
            gc.strokeLine(cx, cy, cx + maxR * Math.cos(angles[i]), cy + maxR * Math.sin(angles[i]));
        }

        // 指标标签
        Color[] indColors = {Color.web("#FF6347"), Color.web("#4169E1"), Color.web("#3CB371"),
                             Color.web("#FFA500"), Color.web("#8B2BE2"), Color.web("#FF1493")};
        gc.setFont(new Font("Microsoft YaHei", 11));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            double labelR = maxR + 14;
            double lx = cx + labelR * Math.cos(angles[i]);
            double ly = cy + labelR * Math.sin(angles[i]);
            gc.setFill(indColors[i]);
            // 根据角度调整偏移，防止标签跑出画布边缘
            if (i == 0) {
                // 顶部（合作事件占比）：文字中心在点上方
                gc.fillText(INDICATORS[i], lx, ly - 6);
            } else if (i == 3) {
                // 底部（冲突事件占比）：文字中心在点下方
                gc.fillText(INDICATORS[i], lx, ly + 16);
            } else {
                gc.fillText(INDICATORS[i], lx, ly + 4);
            }
        }

        // 绘制各区域多边形（跳过隐藏的区域）
        int idx = 0;
        for (Map.Entry<String, double[]> entry : regionData.entrySet()) {
            if (hiddenRegions.contains(entry.getKey())) {
                idx++;
                continue;
            }
            double[] values = entry.getValue();
            double[] xPts = new double[n];
            double[] yPts = new double[n];

            for (int i = 0; i < n; i++) {
                double v = Math.max(0, Math.min(1, values[i]));
                double r = v * maxR;
                xPts[i] = cx + r * Math.cos(angles[i]);
                yPts[i] = cy + r * Math.sin(angles[i]);
            }

            int ci = idx % REGION_FILLS.length;
            gc.setFill(REGION_FILLS[ci]);
            gc.fillPolygon(xPts, yPts, n);
            gc.setStroke(REGION_COLORS[ci]);
            gc.setLineWidth(2);
            gc.strokePolygon(xPts, yPts, n);
            gc.setFill(REGION_COLORS[ci]);
            for (int i = 0; i < n; i++) {
                gc.fillOval(xPts[i] - 3, yPts[i] - 3, 6, 6);
            }
            idx++;
        }

        // 鼠标点击画布检测区域
        radarCanvas.setOnMouseClicked(e -> {
            double mx = e.getX();
            double my = e.getY();
            int ri = 0;
            for (Map.Entry<String, double[]> entry : regionData.entrySet()) {
                if (hiddenRegions.contains(entry.getKey())) {
                    ri++;
                    continue;
                }
                double[] values = entry.getValue();
                double[] xPts = new double[n];
                double[] yPts = new double[n];
                for (int i = 0; i < n; i++) {
                    double v = Math.max(0, Math.min(1, values[i]));
                    double r = v * maxR;
                    xPts[i] = cx + r * Math.cos(angles[i]);
                    yPts[i] = cy + r * Math.sin(angles[i]);
                }
                if (pointInPolygon(mx, my, xPts, yPts)) {
                    showRegionDetail(entry.getKey());
                    return;
                }
                ri++;
            }
            infoLabel.setText("点击雷达图上的区域可查看具体数值");
            infoLabel.setTextFill(Color.web("#7f8c8d"));
        });
    }

    /**
     * 从数据库读取事件数据，按区域分组计算六维指标，结果归一化到 0~1
     * 同时填充 rawRegionValues 保存原始值用于展示
     */
    private Map<String, double[]> computeRegionData() {
        Map<String, double[]> result = new LinkedHashMap<>();
        for (String name : regionNames) {
            result.put(name, null);
        }

        try {
            DaoFactory df;
            try {
                df = DaoFactory.getInstance();
            } catch (IllegalStateException e) {
                DataSource ds = DataSource.getInstance("bri.db");
                ds.initIfAbsent();
                df = DaoFactory.getInstance("bri.db");
            }
            EventDao eventDao = df.eventDao();

            EventQuery q = new EventQuery.Builder()
                .dateFrom(com.yourteam.bri.service.DataService.getActiveFrom().toString())
                .dateTo(com.yourteam.bri.service.DataService.getActiveTo().toString())
                .limit(50000).build();
            List<Event> allEvents = eventDao.query(q);

            // 去重
            Set<String> seenUrls = new HashSet<>();
            List<Event> uniqueEvents = new ArrayList<>();
            for (Event e : allEvents) {
                if (e == null) continue;
                String key = e.getSourceUrl();
                if (key == null || key.trim().isEmpty()) {
                    key = e.getEventId() + "_" + e.getActor1Code() + "_" + e.getEventRoot();
                }
                if (seenUrls.add(key)) {
                    uniqueEvents.add(e);
                }
            }

            // 按区域分组
            Map<String, List<Event>> eventsByRegion = new LinkedHashMap<>();
            for (String regionName : regionNames) {
                eventsByRegion.put(regionName, new ArrayList<>());
            }
            for (Event e : uniqueEvents) {
                String code = e.getActor1Code();
                if (code == null || code.trim().isEmpty()) continue;
                String region = BRICountries.getCountryRegion(code);
                if (region == null) continue;
                List<Event> list = eventsByRegion.get(region);
                if (list != null) {
                    list.add(e);
                }
            }

            // 第一遍：计算原始值
            Map<String, double[]> rawValues = new LinkedHashMap<>();
            Map<String, double[]> rawDisplayValues = new LinkedHashMap<>();
            double maxCoopRatio = 0, maxGoldstein = 0, maxTone = 0;
            double maxConflictRatio = 0, maxEventCount = 0, maxRichness = 0;

            for (String regionName : regionNames) {
                List<Event> events = eventsByRegion.get(regionName);
                if (events == null || events.isEmpty()) {
                    rawValues.put(regionName, new double[]{0,0,0,0,0,0});
                    continue;
                }
                int total = events.size();
                long coopCount = events.stream().filter(Event::isCooperative).count();
                double coopRatio = total > 0 ? (double) coopCount / total : 0;
                long conflictCount = events.stream().filter(Event::isConflict).count();
                double conflictRatio = total > 0 ? (double) conflictCount / total : 0;
                double avgGoldstein = events.stream()
                    .mapToDouble(Event::getGoldstein)
                    .average().orElse(0);
                double avgTone = events.stream()
                    .mapToDouble(Event::getAvgTone)
                    .average().orElse(0);
                long distinctRoots = events.stream()
                    .filter(Event::isCooperative)
                    .map(Event::getEventRoot)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
                double richness = Math.min(distinctRoots / 10.0, 1.0);

                rawValues.put(regionName, new double[]{coopRatio, avgGoldstein, avgTone,
                                                        conflictRatio, (double)total, richness});
                // 保存显示用的原始值（百分比等更直观的格式）
                rawDisplayValues.put(regionName, new double[]{coopRatio * 100, avgGoldstein, avgTone,
                                                               conflictRatio * 100, (double)total, richness * 100});
                if (coopRatio > maxCoopRatio) maxCoopRatio = coopRatio;
                if (avgGoldstein > maxGoldstein) maxGoldstein = avgGoldstein;
                if (avgTone > maxTone) maxTone = avgTone;
                if (conflictRatio > maxConflictRatio) maxConflictRatio = conflictRatio;
                if (total > maxEventCount) maxEventCount = total;
                if (richness > maxRichness) maxRichness = richness;
            }

            // 第二遍：归一化
            for (String regionName : regionNames) {
                double[] raw = rawValues.get(regionName);
                if (raw == null) { result.put(regionName, new double[]{0,0,0,0,0,0}); continue; }
                double d0 = maxCoopRatio > 0 ? raw[0] / maxCoopRatio : 0;
                double d1 = Math.max(0, Math.min(1, (raw[1] + 10) / 20.0));
                double d2 = Math.max(0, Math.min(1, (raw[2] + 100) / 200.0));
                double d3 = maxConflictRatio > 0 ? 1.0 - raw[3] / maxConflictRatio : 1.0;
                d3 = Math.max(0, Math.min(1, d3));
                double d4 = maxEventCount > 0 ? raw[4] / maxEventCount : 0;
                double d5 = maxRichness > 0 ? raw[5] / maxRichness : 0;
                result.put(regionName, new double[]{d0, d1, d2, d3, d4, d5});
            }
            this.rawRegionValues = rawDisplayValues;

        } catch (Exception e) {
            System.err.println("[RadarPanel] 数据计算失败: " + e.getMessage());
            e.printStackTrace();
            for (String name : regionNames) {
                result.put(name, new double[]{0,0,0,0,0,0});
            }
        }
        return result;
    }

    /**
     * 显示指定区域的详细指标数值
     */
    private void showRegionDetail(String regionName) {
        double[] raw = rawRegionValues.get(regionName);
        if (raw == null) {
            infoLabel.setText(regionName + "：暂无数据");
            infoLabel.setTextFill(Color.web("#e74c3c"));
            return;
        }
        String detail = String.format(
            "%s  |  合作事件占比: %.1f%%  |  Goldstein均值: %.2f  |  情感语调: %.2f  |  冲突事件占比: %.1f%%  |  事件总量: %.0f  |  合作丰富度: %.1f%%",
            regionName, raw[0], raw[1], raw[2], raw[3], raw[4], raw[5]
        );
        infoLabel.setText(detail);
        infoLabel.setTextFill(Color.web("#2c3e50"));
    }

    /**
     * 射线法判断点是否在多边形内部
     */
    private boolean pointInPolygon(double px, double py, double[] polyX, double[] polyY) {
        int n = polyX.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((polyY[i] > py) != (polyY[j] > py) &&
                px < (polyX[j] - polyX[i]) * (py - polyY[i]) / (polyY[j] - polyY[i]) + polyX[i]) {
                inside = !inside;
            }
        }
        return inside;
    }
}
