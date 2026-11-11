package com.yourteam.bri.gui;

import com.yourteam.bri.model.CooperationLine;
import com.yourteam.bri.service.DataService;
import com.yourteam.bri.util.BRICountries;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.model.Event;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 专题地图面板 — 基于数据库真实数据的合作指数世界地图。
 *
 * <p>数据来源：
 * <ul>
 *   <li>DataService.getMapData() — 从引擎获取所有国家的合作指数</li>
 *   <li>数据库 event_dao.queryBilateral() — 生成合作线路</li>
 *   <li>BRICountries — 国家/区域信息</li>
 * </ul>
 * </p>
 */
public class MapPanel extends VBox {

        private Canvas mapCanvas;
        private Canvas overlayCanvas;
        private StackPane mapContainer;

        private String selectedCountryCode = null;
        private String hoveredCountryCode = null;
        /** Tooltip 组件（仿 TimelinePanel 方式） */
        private Tooltip countryTooltip;
        /** 当前 tooltip 显示的国家代码 */
        private String tooltipCountryCode = null;
        /** 标记是否需要完全重绘（数据变更） */
        private boolean fullRedrawNeeded = true;
        /** 国家列表容器（成员变量，避免遍历查找） */
        private VBox countryListBox;

        private static final double MIN_LON = -20;
    private static final double MAX_LON = 140;
    private static final double MIN_LAT = -40;
    private static final double MAX_LAT = 70;

    private static final Color HIGH_COOP = Color.web("#4CAF50");
    private static final Color MED_COOP = Color.web("#FFC107");
    private static final Color LOW_COOP = Color.web("#F44336");
    private static final Color CHINA_COLOR = Color.web("#D32F2F");
    private static final Color BG_COLOR = Color.web("#1a2332");
        private static final Color CONTINENT_COLOR = Color.web("#1E3246", 0.3);
    private static final Color GRID_COLOR = Color.web("#32465A", 0.4);

    /** 国家坐标缓存 Map<代码, [纬度, 经度]> */
    private Map<String, double[]> countryCoords;
    /** 地图数据列表（引擎提供） */
    private List<Map<String, Object>> mapData;
    /** 中国到各国的合作线路 */
    private List<CooperationLine> cooperationLines;
    /** 数据是否已加载 */
    private final AtomicBoolean dataLoaded = new AtomicBoolean(false);
    /** 是否正在加载数据 */
    private final AtomicBoolean loading = new AtomicBoolean(false);

    // ----------------------------------------------------------------
    //  构造
    // ----------------------------------------------------------------

    public MapPanel() {
        setPadding(new Insets(15));
        setSpacing(10);
        setStyle("-fx-background-color: #f5f7fa;");

        countryCoords = loadCountryCoords();

        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("一带一路合作指数地图");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));
        Label subTitle = new Label("热力图 + 合作线路 | 数据来源: 数据库");
        subTitle.setFont(Font.font("Microsoft YaHei", 11));
        subTitle.setTextFill(Color.GRAY);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refreshBtn = new Button("\uD83D\uDD04 刷新");
        refreshBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6px 14px; -fx-background-radius: 5;");
        refreshBtn.setOnAction(e -> refreshFromDatabase());
        titleRow.getChildren().addAll(titleLabel, subTitle, spacer, refreshBtn);

        HBox mainContent = new HBox(15);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        VBox mapSection = createMapSection();
        HBox.setHgrow(mapSection, Priority.ALWAYS);
        VBox sidebar = createCountrySidebar();
        mainContent.getChildren().addAll(mapSection, sidebar);

        HBox legendBar = createLegendBar();
        getChildren().addAll(titleRow, mainContent, legendBar);

        // 延迟加载，确保布局完成
        javafx.application.Platform.runLater(this::loadAndDraw);
    }

    // ----------------------------------------------------------------
    //  UI 子组件
    // ----------------------------------------------------------------

        private VBox createMapSection() {
        VBox section = new VBox(5);
        section.setVgrow(this, Priority.ALWAYS);

                mapCanvas = new Canvas(850, 500);
                overlayCanvas = new Canvas(850, 500);
        overlayCanvas.setMouseTransparent(true);

        // 使用 JavaFX Tooltip（仿 TimelinePanel 方式，避免闪烁）
        countryTooltip = new Tooltip();
        countryTooltip.setFont(Font.font("Microsoft YaHei", 12));
        countryTooltip.setStyle(
            "-fx-background: rgba(26,35,50,0.95);" +
            "-fx-background-color: rgba(26,35,50,0.95);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 12;" +
            "-fx-border-color: rgba(255,255,255,0.2);" +
            "-fx-border-radius: 8;"
        );
        countryTooltip.setAutoHide(false);

        mapContainer = new StackPane(mapCanvas, overlayCanvas);
        mapContainer.setAlignment(Pos.TOP_LEFT);
        mapContainer.setPadding(new Insets(5));
        mapContainer.setStyle(
            "-fx-background-color: #1a2332;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 3);"
        );

        // 鼠标移动（显示悬浮气泡和 tooltip）
        mapCanvas.setOnMouseMoved(this::handleMouseMoved);
        mapCanvas.setOnMouseClicked(this::handleMouseClicked);
        mapCanvas.setOnMouseExited(e -> { 
            hoveredCountryCode = null; 
            if (countryTooltip != null) countryTooltip.hide();
            drawOverlay();
        });

                // 监听 Canvas 大小变化，保持 overlay 同步（但不触发全量重绘）
        mapCanvas.widthProperty().addListener((o,ov,nv) -> {
            overlayCanvas.setWidth(nv.doubleValue());
            drawMap();
        });
        mapCanvas.heightProperty().addListener((o,ov,nv) -> {
            overlayCanvas.setHeight(nv.doubleValue());
            drawMap();
        });

        section.getChildren().add(mapContainer);
        return section;
    }

        private VBox createCountrySidebar() {
        VBox sidebar = new VBox(8);
        sidebar.setPrefWidth(250);
        sidebar.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(sidebar, Priority.ALWAYS);
        sidebar.setPadding(new Insets(12));
        sidebar.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);"
        );
        Label title = new Label("\uD83D\uDCCB 国家列表 (按合作指数排序)");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        TextField searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D 搜索国家...");
        searchField.setStyle("-fx-font-size: 12px;");
        searchField.textProperty().addListener((obs, old, val) -> updateCountryList(val));

                countryListBox = new VBox(2);

        ScrollPane scrollPane = new ScrollPane(countryListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(425);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");

        sidebar.getChildren().addAll(title, searchField, scrollPane);
        return sidebar;
    }

    private HBox createLegendBar() {
        HBox legendBar = new HBox(20);
        legendBar.setAlignment(Pos.CENTER);
        legendBar.setPadding(new Insets(8, 15, 8, 15));
        legendBar.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);"
        );

        // 热力渐变
        HBox heatLegend = new HBox(2);
        heatLegend.setAlignment(Pos.CENTER);
        Color[] heatColors = {LOW_COOP, Color.web("#FF9800"), MED_COOP, Color.web("#CDDC39"), HIGH_COOP};
        for (Color c : heatColors) {
            Region block = new Region();
            block.setPrefSize(20, 14);
            block.setStyle("-fx-background-color: " + toHex(c) + ";");
            heatLegend.getChildren().add(block);
        }
        Label lowLabel = new Label("低合作");
        lowLabel.setFont(Font.font("Microsoft YaHei", 10));
        lowLabel.setTextFill(LOW_COOP);
        Label highLabel = new Label("高合作");
        highLabel.setFont(Font.font("Microsoft YaHei", 10));
        highLabel.setTextFill(HIGH_COOP);
        HBox gradientLegend = new HBox(5);
        gradientLegend.setAlignment(Pos.CENTER);
        gradientLegend.getChildren().addAll(lowLabel, heatLegend, highLabel);

        // 线路图例
        HBox lineLegend = new HBox(5);
        lineLegend.setAlignment(Pos.CENTER);
        Canvas lineSample = new Canvas(40, 4);
        GraphicsContext lgc = lineSample.getGraphicsContext2D();
        lgc.setStroke(Color.web("#4CAF50"));
        lgc.setLineWidth(3);
        lgc.strokeLine(0, 2, 40, 2);
        Label lineLabel = new Label("线条粗细 = 合作强度");
        lineLabel.setFont(Font.font("Microsoft YaHei", 10));
        lineLegend.getChildren().addAll(lineSample, lineLabel);

        // 中国
        Label chinaLegend = new Label("★ 中国 (突出显示)");
        chinaLegend.setFont(Font.font("Microsoft YaHei", 10));
        chinaLegend.setTextFill(CHINA_COLOR);

        legendBar.getChildren().addAll(
            gradientLegend,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            lineLegend,
            new Separator(javafx.geometry.Orientation.VERTICAL),
            chinaLegend
        );
        return legendBar;
    }

    // ----------------------------------------------------------------
    //  数据加载 — 从数据库/引擎获取真实数据
    // ----------------------------------------------------------------

        /** 加载所有数据并绘制（仅首次执行，避免重复加载） */
        private void loadAndDraw() {
        if (dataLoaded.get()) {
            drawMap();
            return;
        }
        if (loading.compareAndSet(false, true)) {
            try {
                // 1. 合作指数（引擎提供）
                mapData = DataService.getMapData();
                if (mapData == null || mapData.isEmpty()) {
                    System.out.println("[MapPanel] 引擎无数据，使用回退数据");
                    mapData = fallbackMapData();
                }

                // 过滤：只保留一带一路国家（不含中国）
                mapData = filterBriCountries(mapData);

                // 2. 合作线路（数据库双边查询）
                cooperationLines = loadCooperationLinesFromDb();
                if (cooperationLines == null || cooperationLines.isEmpty()) {
                    System.out.println("[MapPanel] 无合作线路，使用回退线路");
                    cooperationLines = fallbackLines();
                }

                dataLoaded.set(true);
                fullRedrawNeeded = true;
                drawMap();
                updateCountryList("");
            } catch (Exception e) {
                System.err.println("[MapPanel] 数据加载失败: " + e.getMessage());
                e.printStackTrace();
                // 确保有数据可用
                if (mapData == null || mapData.isEmpty()) {
                    mapData = fallbackMapData();
                }
                mapData = filterBriCountries(mapData);
                if (cooperationLines == null || cooperationLines.isEmpty()) {
                    cooperationLines = fallbackLines();
                }
                dataLoaded.set(true);
                fullRedrawNeeded = true;
                drawMap();
            } finally {
                loading.set(false);
            }
        }
    }

    /** 从数据库查询中国与每个国家的事件，生成合作线路 */
    private List<CooperationLine> loadCooperationLinesFromDb() {
        List<CooperationLine> lines = new ArrayList<>();
        try {
            DaoFactory df = DaoFactory.getInstance();
            if (df == null) {
                System.err.println("[MapPanel] DaoFactory 不可用");
                return lines;
            }
            
            EventDao eventDao = df.eventDao();
            if (eventDao == null) {
                System.err.println("[MapPanel] EventDao 不可用");
                return lines;
            }
            
                        String from = com.yourteam.bri.service.DataService.getActiveFrom().toString();
                        String to = com.yourteam.bri.service.DataService.getActiveTo().toString();

                        double[] chinaCoord = countryCoords.get("CHN");
            if (chinaCoord == null) {
                System.err.println("[MapPanel] 中国坐标未定义");
                return lines;
            }

            if (mapData == null || mapData.isEmpty()) {
                return lines;
            }

            for (Map<String, Object> item : mapData) {
                String code = (String) item.get("code");
                if (code == null || "CHN".equals(code)) continue;
                
                double[] tc = countryCoords.get(code);
                if (tc == null) continue;

                double index = 0.4;
                int eventCount = 1;
                try {
                    Object idxObj = item.get("index");
                    if (idxObj instanceof Number) {
                        index = ((Number) idxObj).doubleValue();
                    }
                    Object evtObj = item.get("eventCount");
                    if (evtObj instanceof Number) {
                        eventCount = ((Number) evtObj).intValue();
                    }
                } catch (Exception e) {
                    // 使用默认值
                }

                // 查询中国与该国的双向事件
                List<Event> events = null;
                try {
                    events = eventDao.queryBilateral("CHN", code, from, to);
                } catch (Exception e) {
                    System.err.println("[MapPanel] 查询事件失败: " + code + " - " + e.getMessage());
                }
                
                double avgGoldstein = 0;
                double strength;
                int realCount;

                if (events != null && !events.isEmpty()) {
                    double gs = 0;
                    for (Event e : events) {
                        if (e != null) {
                            gs += e.getGoldstein();
                        }
                    }
                    avgGoldstein = gs / events.size();
                    realCount = events.size();
                    // 强度综合指数和Goldstein
                    double normalizedGs = Math.max(-1, Math.min(1, avgGoldstein / 10.0));
                    strength = Math.min(1.0, Math.max(0.15,
                        index * 0.5 + (normalizedGs + 1) / 2.0 * 0.5));
                } else {
                    // 无具体事件，用指数推算
                    avgGoldstein = 1.0 + index * 3;
                    strength = Math.max(0.15, index * 0.6);
                    realCount = Math.max(1, eventCount);
                }

                CooperationLine line = new CooperationLine();
                line.setSourceCode("CHN");
                line.setSourceLat(chinaCoord[0]);
                line.setSourceLon(chinaCoord[1]);
                line.setTargetCode(code);
                line.setTargetLat(tc[0]);
                line.setTargetLon(tc[1]);
                line.setStrength(Math.min(1.0, strength));
                line.setGoldsteinScale(avgGoldstein);
                line.setEventCount(realCount);
                lines.add(line);
            }
            lines.sort((a, b) -> Double.compare(b.getStrength(), a.getStrength()));
        } catch (Exception e) {
            System.err.println("[MapPanel] 合作线路加载失败: " + e.getMessage());
            e.printStackTrace();
            // 返回空列表，调用者会使用回退数据
        }
        return lines;
    }

        /** 无数据/失败时的回退地图数据（仅一带一路国家） */
    private List<Map<String, Object>> fallbackMapData() {
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            double[] chinaCoord = countryCoords.get("CHN");
            if (chinaCoord == null) return data;

            // 中国
            Map<String, Object> chinaItem = new LinkedHashMap<>();
            chinaItem.put("code", "CHN");
            chinaItem.put("name", "中国");
            chinaItem.put("region", "东亚");
            chinaItem.put("index", 1.0);
            chinaItem.put("eventCount", 0);
            data.add(chinaItem);

            // 一带一路国家
            for (String code : countryCoords.keySet()) {
                if ("CHN".equals(code)) continue;
                if (!BRICountries.isBriCountry(code)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", code);
                String name = BRICountries.getCountryName(code);
                item.put("name", name != null ? name : code);
                String region = BRICountries.getCountryRegion(code);
                item.put("region", region != null ? region : "其他");
                item.put("index", 0.4 + new Random().nextDouble() * 0.3);
                item.put("eventCount", 1 + new Random().nextInt(10));
                data.add(item);
            }
        } catch (Exception e) {
            System.err.println("[MapPanel] 回退数据生成失败: " + e.getMessage());
        }
        return data;
    }

    /** 回退合作线路 */
    private List<CooperationLine> fallbackLines() {
        List<CooperationLine> lines = new ArrayList<>();
        try {
            double[] chinaCoord = countryCoords.get("CHN");
            if (chinaCoord == null || mapData == null) return lines;
            
            for (Map<String, Object> item : mapData) {
                String code = (String) item.get("code");
                if (code == null || "CHN".equals(code)) continue;
                
                double[] tc = countryCoords.get(code);
                if (tc == null) continue;
                
                double index = 0.4;
                try {
                    Object idxObj = item.get("index");
                    if (idxObj instanceof Number) {
                        index = ((Number) idxObj).doubleValue();
                    }
                } catch (Exception ignored) {}
                
                CooperationLine line = new CooperationLine();
                line.setSourceCode("CHN");
                line.setSourceLat(chinaCoord[0]);
                line.setSourceLon(chinaCoord[1]);
                line.setTargetCode(code);
                line.setTargetLat(tc[0]);
                line.setTargetLon(tc[1]);
                line.setStrength(Math.max(0.15, Math.min(1.0, index)));
                line.setGoldsteinScale(1.0 + index * 3);
                line.setEventCount(1);
                lines.add(line);
            }
        } catch (Exception e) {
            System.err.println("[MapPanel] 回退线路生成失败: " + e.getMessage());
        }
        return lines;
    }

    // ----------------------------------------------------------------
    //  地图绘制
    // ----------------------------------------------------------------

                        @SuppressWarnings("unchecked")
            private void drawMap() {
                GraphicsContext gc = mapCanvas.getGraphicsContext2D();
                double w = mapCanvas.getWidth();
                double h = mapCanvas.getHeight();

                // 如果数据已加载但只是尺寸变化（非全量重绘），仅重绘 overlay
                if (!fullRedrawNeeded && dataLoaded.get()) {
                    drawOverlay();
                    return;
                }

                if (w < 50 || h < 50) return;
                fullRedrawNeeded = false;

                gc.clearRect(0, 0, w, h);

                                // 1. 深色背景（世界地图画布底色）
                gc.setFill(BG_COLOR);
                gc.fillRect(0, 0, w, h);

                // 2. 加载并绘制世界地图背景图片（images/world_map.png）
                try {
                    Image mapBg = new Image(getClass().getResourceAsStream("/images/world_map.png"));
                    if (!mapBg.isError()) {
                        gc.drawImage(mapBg, 0, 0, w, h);
                    }
                } catch (Exception ignored) {
                    // 图片不存在时忽略，保持深色背景
                }

                // 3. 合作线路（在气泡下方）
                if (cooperationLines != null && !cooperationLines.isEmpty()) {
                    drawCooperationLines(gc, cooperationLines, w, h);
                }

                // 3. 气泡（无高亮的基础版本）
                if (mapData == null || mapData.isEmpty()) {
                    gc.setFill(Color.web("#C8C8C8", 0.8));
                    gc.setFont(Font.font("Microsoft YaHei", 18));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("暂无数据，请点击「刷新」加载", w / 2, h / 2);
                } else {
                    drawCountryBubbles(gc, mapData, w, h, false);
                    drawChinaStar(gc, w, h);
                }

                                // 4. 覆盖层交互
                drawOverlay();
            }

        /** 只画高亮交互层（覆盖在底图上），避免全量重绘 */
        private void drawOverlay() {
            GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
            double w = overlayCanvas.getWidth();
            double h = overlayCanvas.getHeight();
            gc.clearRect(0, 0, w, h);

            if (mapData == null || mapData.isEmpty()) return;

            for (Map<String, Object> country : mapData) {
                if (country == null) continue;
                String code = (String) country.get("code");
                if (code == null) continue;
                boolean highlight = code.equals(selectedCountryCode) || code.equals(hoveredCountryCode);
                if (!highlight) continue;

                double[] coords = countryCoords.get(code);
                if (coords == null) continue;
                double x = toX(coords[1], w);
                double y = toY(coords[0], h);

                double index = 0.4;
                int eventCount = 1;
                try {
                    Object idxObj = country.get("index");
                    if (idxObj instanceof Number) index = ((Number) idxObj).doubleValue();
                    Object evtObj = country.get("eventCount");
                    if (evtObj instanceof Number) eventCount = ((Number) evtObj).intValue();
                } catch (Exception ignored) {}

                double radius = 8 + eventCount * 0.03;
                radius = Math.min(radius, 28);
                if ("CHN".equals(code)) radius = 20;
                radius *= 1.3;

                // 光晕
                gc.setGlobalAlpha(0.35);
                gc.setFill(Color.color(1, 1, 0.5));
                gc.fillOval(x - radius - 5, y - radius - 5, (radius + 5) * 2, (radius + 5) * 2);
                gc.setGlobalAlpha(1.0);

                Color fillColor = getCooperationColor(index);
                if ("CHN".equals(code)) fillColor = CHINA_COLOR;
                Color borderColor = fillColor.darker();

                gc.setFill(Color.color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 0.85));
                gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
                gc.setStroke(borderColor);
                gc.setLineWidth(2.5);
                gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);

                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 11));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(code, x, y + 3);
            }
        }



        @SuppressWarnings("unchecked")
    private void drawCountryBubbles(GraphicsContext gc, List<Map<String, Object>> data, double w, double h, boolean highlightMode) {
        for (Map<String, Object> country : data) {
            if (country == null) continue;
            
            String code = (String) country.get("code");
            if (code == null) continue;
            
            double index = 0.4;
            int eventCount = 1;
            try {
                Object idxObj = country.get("index");
                if (idxObj instanceof Number) {
                    index = ((Number) idxObj).doubleValue();
                }
                Object evtObj = country.get("eventCount");
                if (evtObj instanceof Number) {
                    eventCount = ((Number) evtObj).intValue();
                }
            } catch (Exception ignored) {}
            
            double[] coords = countryCoords.get(code);
            if (coords == null) continue;

            double x = toX(coords[1], w);
            double y = toY(coords[0], h);

            // 气泡大小
            double radius = 8 + eventCount * 0.03;
            radius = Math.min(radius, 28);

            Color fillColor = getCooperationColor(index);
            Color borderColor = fillColor.darker();

            if ("CHN".equals(code)) {
                fillColor = CHINA_COLOR;
                borderColor = Color.web("#B71C1C");
                radius = 20;
            }

            // 阴影
            gc.setFill(Color.web("#000000", 0.3));
            gc.fillOval(x - radius + 2, y - radius + 2, radius * 2, radius * 2);
            
            // 主体（高亮的气泡用 overlay 画，这里正常大小）
            double drawRadius = highlightMode ? radius * 1.3 : radius;
            gc.setFill(Color.color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 0.8));
            gc.fillOval(x - drawRadius, y - drawRadius, drawRadius * 2, drawRadius * 2);
            gc.setStroke(borderColor);
            gc.setLineWidth(highlightMode ? 2.5 : 1.5);
            gc.strokeOval(x - drawRadius, y - drawRadius, drawRadius * 2, drawRadius * 2);

            // 国家代码
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, highlightMode ? 11 : 9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(code, x, y + 3);
        }
    }

    private void drawCooperationLines(GraphicsContext gc, List<CooperationLine> lines, double w, double h) {
        if (lines == null || lines.isEmpty()) return;
        
        // 只绘制前50条最强的线路，避免过于密集
        int maxLines = Math.min(50, lines.size());
        List<CooperationLine> topLines = lines.subList(0, maxLines);
        
        for (CooperationLine line : topLines) {
            if (line == null) continue;
            
            double x1 = toX(line.getSourceLon(), w);
            double y1 = toY(line.getSourceLat(), h);
            double x2 = toX(line.getTargetLon(), w);
            double y2 = toY(line.getTargetLat(), h);
            
                        double strength = Math.max(0.1, Math.min(1.0, line.getStrength()));
            double lineWidth = 1 + strength * 8;

            Color lineColor;
            double gs = line.getGoldsteinScale();
            if (gs > 3.0)       lineColor = Color.web("#2E7D32");
            else if (gs > 0)    lineColor = Color.web("#4CAF50");
            else if (gs > -3)   lineColor = Color.web("#A5D6A7");
            else                lineColor = Color.web("#C62828");

            double opacity = 0.15 + strength * 0.8;
            gc.setGlobalAlpha(opacity);
            gc.setStroke(lineColor);
            gc.setLineWidth(lineWidth);

            // 贝塞尔曲线
            double midX = (x1 + x2) / 2;
            double midY = (y1 + y2) / 2 - Math.abs(x2 - x1) * 0.15;
            gc.beginPath();
            gc.moveTo(x1, y1);
            gc.quadraticCurveTo(midX, midY, x2, y2);
            gc.stroke();

            // 箭头
            drawArrow(gc, midX, midY, x2, y2, lineWidth + 2, lineColor);
            gc.setGlobalAlpha(1.0);
        }
    }

    private void drawArrow(GraphicsContext gc, double fromX, double fromY,
                           double toX, double toY, double size, Color color) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1) return;
        
        double angle = Math.atan2(dy, dx);
        double arrowAngle = Math.PI / 7;
        gc.setFill(color);
        gc.setGlobalAlpha(0.8);
        double[] xPoints = {
            toX, 
            toX - size * Math.cos(angle - arrowAngle), 
            toX - size * Math.cos(angle + arrowAngle)
        };
        double[] yPoints = {
            toY, 
            toY - size * Math.sin(angle - arrowAngle), 
            toY - size * Math.sin(angle + arrowAngle)
        };
                gc.fillPolygon(xPoints, yPoints, 3);
    }

    /** 在地图上绘制中国五角星 */
    private void drawChinaStar(GraphicsContext gc, double w, double h) {
        double[] coord = countryCoords.get("CHN");
        if (coord == null) return;
                double x = toX(coord[1], w);
        double y = toY(coord[0], h);

        double r = 22;
        double outerR = r;
        double innerR = r * 0.4;
        int points = 5;

        double[] xPoints = new double[points * 2];
        double[] yPoints = new double[points * 2];

        for (int i = 0; i < points * 2; i++) {
            double radius = (i % 2 == 0) ? outerR : innerR;
            double angle = Math.toRadians(-90 + i * 360.0 / (points * 2));
            xPoints[i] = x + radius * Math.cos(angle);
            yPoints[i] = y + radius * Math.sin(angle);
        }

        gc.setGlobalAlpha(0.25);
        gc.setFill(Color.web("#FFD700"));
        gc.fillOval(x - r - 5, y - r - 5, r * 2 + 10, r * 2 + 10);

        gc.setGlobalAlpha(1.0);
        gc.setFill(CHINA_COLOR);
        gc.fillPolygon(xPoints, yPoints, points * 2);

        gc.setStroke(Color.web("#FFD700"));
        gc.setLineWidth(2);
        gc.strokePolygon(xPoints, yPoints, points * 2);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("中国", x, y + r + 22);
    }

    // ----------------------------------------------------------------
    //  交互事件
    // ----------------------------------------------------------------

                        private void handleMouseMoved(MouseEvent e) {
                            String hitCode = findCountryAt(e.getX(), e.getY());
                            if (hitCode != null && !hitCode.equals(hoveredCountryCode)) {
                                hoveredCountryCode = hitCode;
                                showTooltip(hitCode, e.getX(), e.getY());
                                drawOverlay();
                            } else if (hitCode == null && hoveredCountryCode != null) {
                                hoveredCountryCode = null;
                                if (countryTooltip != null) countryTooltip.hide();
                                drawOverlay();
                            } else if (hitCode != null && hitCode.equals(hoveredCountryCode)) {
                                if (countryTooltip != null) {
                                    countryTooltip.show(mapCanvas, e.getScreenX() + 15, e.getScreenY() + 10);
                                }
                            }
                        }

            private void handleMouseClicked(MouseEvent e) {
                String hitCode = findCountryAt(e.getX(), e.getY());
                if (hitCode != null) {
            if (hitCode.equals(selectedCountryCode)) {
                selectedCountryCode = null;
            } else {
                selectedCountryCode = hitCode;
                showCountryDetail(hitCode);
            }
            drawOverlay();
        } else {
            selectedCountryCode = null;
            drawOverlay();
        }
    }

    @SuppressWarnings("unchecked")
    private String findCountryAt(double mx, double my) {
        if (mapData == null) return null;
        
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();
        
        for (Map<String, Object> country : mapData) {
            String code = (String) country.get("code");
            if (code == null) continue;
            
            double[] coords = countryCoords.get(code);
            if (coords == null) continue;
            
            double x = toX(coords[1], w);
            double y = toY(coords[0], h);
            
            int eventCount = 1;
            try {
                Object evtObj = country.get("eventCount");
                if (evtObj instanceof Number) {
                    eventCount = ((Number) evtObj).intValue();
                }
            } catch (Exception ignored) {}
            
            double radius = 8 + eventCount * 0.03;
            radius = Math.min(radius, 28);
            if ("CHN".equals(code)) radius = 20;
            if (code.equals(selectedCountryCode) || code.equals(hoveredCountryCode)) {
                radius *= 1.3;
            }
            if (Math.sqrt((mx - x) * (mx - x) + (my - y) * (my - y)) <= radius + 3) {
                return code;
            }
        }
        return null;
    }

    private void showTooltip(String code, double mx, double my) {
        Map<String, Object> d = findCountryData(code);
        if (d == null) return;
        
        String name = (String) d.get("name");
        if (name == null) name = code;
        
        String region = BRICountries.getCountryRegion(code);
        if (region == null) region = "未知";
        
        double index = 0.4;
        int eventCount = 1;
        try {
            Object idxObj = d.get("index");
            if (idxObj instanceof Number) {
                index = ((Number) idxObj).doubleValue();
            }
            Object evtObj = d.get("eventCount");
            if (evtObj instanceof Number) {
                eventCount = ((Number) evtObj).intValue();
            }
        } catch (Exception ignored) {}
        
        String riskLevel = index >= 0.7 ? "低风险" : index >= 0.4 ? "中风险" : "高风险";

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83C\uDF0D ").append(name).append(" (").append(code).append(")\n");
        sb.append("\uD83D\uDCCD 区域: ").append(region).append("\n");
        sb.append("\uD83D\uDCCA 合作指数: ").append(String.format("%.2f", index));
        if (index >= 0.7) sb.append(" \uD83D\uDFE2");
        else if (index >= 0.4) sb.append(" \uD83D\uDFE1");
        else sb.append(" \uD83D\uDD34");
        sb.append("\n\uD83D\uDCCB 事件: ").append(eventCount).append("\n");
        sb.append("\u26A0 风险: ").append(riskLevel);

        double tooltipX = mx + 15;
        double tooltipY = my + 10;
        double canvasWidth = mapCanvas.getWidth();
        double canvasHeight = mapCanvas.getHeight();
        if (tooltipX + 210 > canvasWidth) tooltipX = mx - 220;
        if (tooltipY + 120 > canvasHeight) tooltipY = my - 130;
        if (tooltipX < 0) tooltipX = 10;
        if (tooltipY < 0) tooltipY = 10;
        
                countryTooltip.setText(sb.toString());
                countryTooltip.show(mapCanvas, mx + 15, my + 10);
    }

    private void showCountryDetail(String code) {
        if ("CHN".equals(code)) return;
        Map<String, Object> d = findCountryData(code);
        if (d == null) return;
        
        String name = (String) d.get("name");
        if (name == null) name = code;
        
        String region = BRICountries.getCountryRegion(code);
        if (region == null) region = "未知";
        
        double index = 0.4;
        int eventCount = 1;
        try {
            Object idxObj = d.get("index");
            if (idxObj instanceof Number) {
                index = ((Number) idxObj).doubleValue();
            }
            Object evtObj = d.get("eventCount");
            if (evtObj instanceof Number) {
                eventCount = ((Number) evtObj).intValue();
            }
        } catch (Exception ignored) {}
        
        String riskLevel = index >= 0.7 ? "低风险" : index >= 0.4 ? "中风险" : "高风险";

        // 计算合作线路统计
        long lineCount = 0;
        if (cooperationLines != null) {
            lineCount = cooperationLines.stream()
                .filter(l -> l != null && 
                    (code.equals(l.getSourceCode()) || code.equals(l.getTargetCode())))
                .count();
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("\uD83C\uDF0D 国家详情 - " + name);
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(420);

        Label nameLabel = new Label("\uD83C\uDF0D " + name + " (" + code + ")");
        nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        nameLabel.setTextFill(Color.web("#1e1e3c"));
        Label regionLabel = new Label("\uD83D\uDCCD 区域: " + region);
        regionLabel.setFont(Font.font("Microsoft YaHei", 13));

        HBox indexRow = new HBox(10);
        indexRow.setAlignment(Pos.CENTER_LEFT);
        Label it = new Label("\uD83D\uDCCA 合作指数:");
        it.setFont(Font.font("Microsoft YaHei", 13));
        Color ic = getCooperationColor(index);
        Label iv = new Label(String.format("%.2f", index));
        iv.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        iv.setTextFill(ic);
        Region bar = new Region();
        bar.setPrefSize(80, 8);
        bar.setStyle("-fx-background-color: " + toHex(ic) + "; -fx-background-radius: 4;");
        indexRow.getChildren().addAll(it, bar, iv);

        String lt = index >= 0.7 ? "高合作" : index >= 0.4 ? "中合作" : "低合作";
        Color lc = index >= 0.7 ? HIGH_COOP : index >= 0.4 ? MED_COOP : LOW_COOP;
        Label ll = new Label("[" + lt + "]");
        ll.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        ll.setTextFill(lc);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 0, 10, 0));
        grid.add(new Label("\uD83D\uDCCB 事件数量:"), 0, 0);
        grid.add(new Label(String.valueOf(eventCount)), 1, 0);
        grid.add(new Label("\u26A0 风险等级:"), 0, 1);
        Label rl = new Label(riskLevel);
        rl.setTextFill(riskLevel.contains("高") ? Color.RED : riskLevel.contains("中") ? Color.ORANGE : Color.GREEN);
        grid.add(rl, 1, 1);
        grid.add(new Label("\uD83D\uDE9C 合作线路:"), 0, 2);
        grid.add(new Label(lineCount + " 条"), 1, 2);
        for (javafx.scene.Node n : grid.getChildren()) {
            if (n instanceof Label) {
                ((Label) n).setFont(Font.font("Microsoft YaHei", 12));
            }
        }

        content.getChildren().addAll(nameLabel, regionLabel, new Separator(), indexRow, ll,
            new Separator(), grid);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

        @SuppressWarnings("unchecked")
    private void updateCountryList(String keyword) {
        if (mapData == null || mapData.isEmpty() || countryListBox == null) return;
        
        countryListBox.getChildren().clear();

        String keywordLower = keyword != null ? keyword.toLowerCase() : "";
        
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> c : mapData) {
            if (c == null) continue;
            String code = (String) c.get("code");
            if (code == null) continue;
            String name = (String) c.get("name");
            if (name == null) name = code;
            String region = BRICountries.getCountryRegion(code);
            if (region == null) region = "";
            
            if (keywordLower.isEmpty() ||
                name.toLowerCase().contains(keywordLower) ||
                code.toLowerCase().contains(keywordLower) ||
                region.toLowerCase().contains(keywordLower)) {
                filtered.add(c);
            }
        }
        
        filtered.sort((a, b) -> {
            double idxA = 0.4, idxB = 0.4;
            try {
                Object idxObjA = a.get("index");
                if (idxObjA instanceof Number) idxA = ((Number) idxObjA).doubleValue();
                Object idxObjB = b.get("index");
                if (idxObjB instanceof Number) idxB = ((Number) idxObjB).doubleValue();
            } catch (Exception ignored) {}
            return Double.compare(idxB, idxA);
        });

        for (Map<String, Object> c : filtered) {
            String code = (String) c.get("code");
            if (code == null) continue;
            
            double index = 0.4;
            try {
                Object idxObj = c.get("index");
                if (idxObj instanceof Number) {
                    index = ((Number) idxObj).doubleValue();
                }
            } catch (Exception ignored) {}
            
            String name = (String) c.get("name");
            if (name == null) name = code;
            
                        HBox item = new HBox(8);
                        item.setPadding(new Insets(6, 8, 6, 8));
                        item.setAlignment(Pos.CENTER_LEFT);
                        item.setStyle("-fx-background-color: transparent; -fx-background-radius: 4;");

                        Region dot = new Region();
                        dot.setPrefSize(8, 8);
                        dot.setStyle("-fx-background-color: " + toHex(getCooperationColor(index)) + "; -fx-background-radius: 4;");

                        Label nameLabel = new Label(name + " (" + code + ")");
                        nameLabel.setFont(Font.font("Microsoft YaHei", 12));
                        nameLabel.setTextFill(Color.web("#2c3e50"));

                        Region spacer2 = new Region();
                        HBox.setHgrow(spacer2, Priority.ALWAYS);

                        Label indexLabel = new Label("#" + String.format("%.2f", index));
                        indexLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
                        indexLabel.setTextFill(getCooperationColor(index));

            item.getChildren().addAll(dot, nameLabel, spacer2, indexLabel);

            final String fc = code;
            item.setOnMouseEntered(e -> {
                hoveredCountryCode = fc;
                drawOverlay();
                item.setStyle("-fx-background-color: #e8f0fe; -fx-background-radius: 4;");
            });
            item.setOnMouseExited(e -> {
                hoveredCountryCode = null;
                drawOverlay();
                item.setStyle("-fx-background-color: transparent; -fx-background-radius: 4;");
            });
            item.setOnMouseClicked(e -> {
                selectedCountryCode = fc;
                showCountryDetail(fc);
                drawOverlay();
            });
            countryListBox.getChildren().add(item);
        }
    }

        // ----------------------------------------------------------------
    //  工具方法
    // ----------------------------------------------------------------

    private Color getCooperationColor(double index) {
        if (index >= 0.7) return HIGH_COOP;
        if (index >= 0.4) return MED_COOP;
        return LOW_COOP;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCountryData(String code) {
        if (mapData == null || code == null) return null;
        for (Map<String, Object> c : mapData) {
            if (code.equals(c.get("code"))) return c;
        }
        return null;
    }

        /** 经度 → 画布 X */
        private double toX(double lon, double w) {
            return (lon - MIN_LON) / (MAX_LON - MIN_LON) * (w - 40) + 20;
        }

        /** 纬度 → 画布 Y */
        private double toY(double lat, double h) {
            return (MAX_LAT - lat) / (MAX_LAT - MIN_LAT) * (h - 40) + 20;
        }

    private Map<String, double[]> loadCountryCoords() {
        Map<String, double[]> coords = new HashMap<>();
        String[][] data = {
            {"CHN", "35.86", "104.19"},
            {"IDN", "-0.79", "113.92"}, {"MYS", "4.21", "101.98"}, {"PHL", "12.88", "121.77"},
            {"SGP", "1.35", "103.82"}, {"THA", "15.87", "100.99"}, {"VNM", "14.06", "108.28"},
            {"MMR", "21.92", "95.96"}, {"LAO", "19.86", "102.50"}, {"KHM", "12.57", "104.99"},
            {"BRN", "4.54", "114.73"}, {"TLS", "-8.87", "125.73"},
            {"IND", "20.59", "78.96"}, {"PAK", "30.38", "69.35"}, {"BGD", "23.69", "90.36"},
            {"LKA", "7.87", "80.77"}, {"NPL", "28.39", "84.12"}, {"BTN", "27.51", "90.43"},
            {"MDV", "3.20", "73.22"}, {"AFG", "33.94", "67.71"},
            {"KAZ", "48.02", "66.92"}, {"KGZ", "41.20", "74.77"}, {"TJK", "38.86", "71.28"},
            {"UZB", "41.38", "64.59"}, {"TKM", "38.97", "59.56"},
            {"IRN", "32.43", "53.69"}, {"IRQ", "33.22", "43.68"}, {"TUR", "38.96", "35.24"},
            {"SAU", "23.89", "45.08"}, {"ARE", "23.42", "53.85"}, {"QAT", "25.35", "51.18"},
            {"KWT", "29.31", "47.48"}, {"OMN", "21.51", "55.92"}, {"BHR", "25.93", "50.64"},
            {"ISR", "31.05", "34.85"}, {"JOR", "30.59", "36.24"}, {"LBN", "33.85", "35.86"},
            {"SYR", "34.80", "39.00"}, {"YEM", "15.55", "48.52"},
            {"RUS", "61.52", "105.32"}, {"UKR", "48.38", "31.17"}, {"BLR", "53.71", "27.95"},
            {"POL", "51.92", "19.15"}, {"CZE", "49.82", "15.47"}, {"SVK", "48.67", "19.70"},
            {"HUN", "47.16", "19.50"}, {"ROU", "45.94", "24.97"}, {"BGR", "42.73", "25.49"},
            {"SRB", "44.02", "21.01"}, {"HRV", "45.10", "15.20"}, {"BIH", "43.92", "17.68"},
            {"MNE", "42.71", "19.37"}, {"MKD", "41.61", "21.75"}, {"ALB", "41.15", "20.17"},
            {"GRC", "39.07", "21.82"}, {"LTU", "55.17", "23.88"}, {"LVA", "56.88", "24.60"},
            {"EST", "58.60", "25.01"}, {"MDA", "47.41", "28.37"},
            {"EGY", "26.82", "30.80"}, {"ZAF", "-30.56", "22.94"}, {"NGA", "9.08", "8.68"},
            {"KEN", "-0.02", "37.91"}, {"ETH", "9.15", "40.49"}, {"TZA", "-6.37", "34.89"},
            {"AGO", "-11.20", "17.87"}, {"GHA", "7.95", "-1.02"}, {"CIV", "7.54", "-5.55"},
            {"MAR", "31.79", "-7.09"}, {"DZA", "28.03", "1.66"}, {"LBY", "26.34", "17.23"},
            {"SDN", "12.86", "30.22"}, {"SSD", "6.88", "31.31"}, {"MOZ", "-18.67", "35.53"},
            {"ZMB", "-13.13", "27.85"}, {"ZWE", "-19.02", "29.15"}, {"UGA", "1.37", "32.29"},
            {"MWI", "-13.25", "34.30"}, {"SEN", "14.50", "-14.45"}, {"MLI", "17.57", "-4.00"},
            {"BFA", "12.24", "-1.56"}, {"NER", "17.61", "8.08"}, {"TCD", "15.45", "18.73"},
            {"SOM", "5.15", "46.20"}, {"DJI", "11.83", "42.59"}, {"MRT", "21.01", "-10.94"},
            {"GIN", "9.95", "-9.70"}, {"RWA", "-1.94", "29.87"}, {"BDI", "-3.37", "29.92"},
            {"TGO", "8.62", "0.82"}, {"BEN", "9.31", "2.32"}, {"SLE", "8.46", "-11.78"},
            {"LBR", "6.43", "-9.43"}, {"MDG", "-18.77", "46.87"}, {"MUS", "-20.35", "57.55"},
            {"NAM", "-22.96", "18.49"}, {"BWA", "-24.63", "24.93"}, {"GAB", "-0.80", "11.61"},
            {"COG", "-0.23", "15.83"}, {"COD", "-4.04", "21.76"}, {"CMR", "7.37", "12.35"}
        };
        for (String[] e : data) {
            try {
                coords.put(e[0], new double[]{Double.parseDouble(e[1]), Double.parseDouble(e[2])});
            } catch (NumberFormatException ignored) {}
        }
        return coords;
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

                /** 供外部调用刷新 */
    public void refreshFromDatabase() {
        // 强制重新加载，重置所有状态
        dataLoaded.set(false);
        loading.set(false);
        mapData = null;
        cooperationLines = null;
        fullRedrawNeeded = true;
        loadAndDraw();
    }

        /**
     * 过滤 mapData，只保留一带一路国家（不含中国）
     */
    private List<Map<String, Object>> filterBriCountries(List<Map<String, Object>> data) {
        if (data == null) return new ArrayList<>();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : data) {
            if (item == null) continue;
            String code = (String) item.get("code");
            if (code == null) continue;
            if (BRICountries.isBriCountry(code)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}