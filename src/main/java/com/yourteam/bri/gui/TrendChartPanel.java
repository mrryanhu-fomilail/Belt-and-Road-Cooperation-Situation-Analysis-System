package com.yourteam.bri.gui;

import com.yourteam.bri.model.CooperationIndex;
import com.yourteam.bri.util.BRICountries;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.model.MonthlyTrend;
import com.bri.config.DataSource;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 趋势分析面板 — 折线图展示中国与各一带一路国家的合作指数走势。
 *
 * <p>数据来源：直接从数据库 {@code bri.db} 的 events 表中查询中国与指定国家间的月度双边趋势数据，
 * 使用 {@link EventDao#monthlyBilateralTrend(String, String, String)} 方法。</p>
 *
 * <p>画布尺寸自适应父容器，国家切换时自动重新加载。</p>
 */
public class TrendChartPanel extends VBox {

    private Canvas trendCanvas;
    private ComboBox<String> countryCombo;
    private Label avgIndexLabel, trendLabel, peakLabel, valleyLabel, statusInfoLabel, filteredInfoLabel;

    // 使用 AtomicReference 保证线程安全
    private final AtomicReference<List<CooperationIndex>> currentDataRef = new AtomicReference<>(new ArrayList<>());
    /** 所有一带一路国家（六大区域，排除中国）的代码列表 */
    private final List<String> briCountryCodes = new ArrayList<>();
    /** 是否已完成有数据国家的筛选 */
    private volatile boolean filtered = false;

    // ==================== 构造 ====================

    public TrendChartPanel() {
        setPadding(new Insets(15));
        setSpacing(10);
        setStyle("-fx-background-color: #f5f7fa;");

        // 初始化一带一路国家代码列表
        Set<String> briSet = new HashSet<>();
        for (String region : BRICountries.getMainRegions()) {
            briSet.addAll(BRICountries.getCountriesByRegion(region));
        }
        for (String code : briSet) {
            if (!"CHN".equals(code)) {
                briCountryCodes.add(code);
            }
        }

        Label titleLabel = new Label("\uD83D\uDCC8 关系趋势分析");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        HBox controlBar = createControlBar();

        // ========== 画布 ==========
        trendCanvas = new Canvas(100, 100);

        StackPane canvasWrap = new StackPane(trendCanvas);
        canvasWrap.setPadding(new Insets(0));
        canvasWrap.setMinHeight(300);
        canvasWrap.setPrefHeight(450);
        canvasWrap.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");
        VBox.setVgrow(canvasWrap, Priority.ALWAYS);

        // Canvas 尺寸跟随父容器
        canvasWrap.widthProperty().addListener((o, ov, nv) -> {
            double w = nv.doubleValue() - 8;
            if (w > 50 && Math.abs(w - trendCanvas.getWidth()) > 5) {
                trendCanvas.setWidth(w);
                Platform.runLater(this::redrawChart);
            }
        });
        canvasWrap.heightProperty().addListener((o, ov, nv) -> {
            double h = nv.doubleValue() - 8;
            if (h > 50 && Math.abs(h - trendCanvas.getHeight()) > 5) {
                trendCanvas.setHeight(h);
                Platform.runLater(this::redrawChart);
            }
        });

        VBox statsBox = createStatsBox();

        getChildren().addAll(titleLabel, controlBar, canvasWrap, statsBox);

        // 首次布局完成后：后台检测有数据的国家，更新下拉列表，加载第一个国家
        Platform.runLater(() -> {
            double pw = getWidth();
            double ph = getHeight();
            if (pw > 200 && ph > 200) {
                trendCanvas.setWidth(pw - 30);
                trendCanvas.setHeight(Math.max(300, ph - 200));
            } else {
                trendCanvas.setWidth(800);
                trendCanvas.setHeight(450);
            }

            ensureDatabaseReady();
            // 后台检测哪些一带一路国家有数据库数据
            new Thread(() -> {
                try {
                    Set<String> hasData = new HashSet<>();
                    DaoFactory df = DaoFactory.getInstance();
                    EventDao eventDao = df.eventDao();
                    String from = com.yourteam.bri.service.DataService.getActiveFrom().toString();
                    String to = com.yourteam.bri.service.DataService.getActiveTo().toString();

                    for (String code : briCountryCodes) {
                        List<MonthlyTrend> t = eventDao.monthlyBilateralTrend(code, from, to);
                        if (t != null && !t.isEmpty()) {
                            hasData.add(code);
                        }
                    }

                    Platform.runLater(() -> {
                        // 重建下拉列表，只保留有数据的国家
                        countryCombo.getItems().clear();
                        List<String> validItems = new ArrayList<>();
                        for (String code : briCountryCodes) {
                            if (hasData.contains(code)) {
                                validItems.add(BRICountries.getCountryName(code) + " (" + code + ")");
                            }
                        }
                        Collections.sort(validItems);
                        countryCombo.getItems().addAll(validItems);

                        filtered = true;

                        if (!validItems.isEmpty()) {
                            // 选第一个有数据的国家并加载
                            countryCombo.getSelectionModel().select(0);
                        } else {
                            statusInfoLabel.setText("\u26A0 一带一路国家均无数据库数据");
                            statusInfoLabel.setTextFill(Color.web("#e74c3c"));
                            drawEmptyChart(trendCanvas.getGraphicsContext2D(),
                                trendCanvas.getWidth(), trendCanvas.getHeight());
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[TrendChartPanel] 数据检测异常: " + e.getMessage());
                    Platform.runLater(() -> {
                        statusInfoLabel.setText("\u274C 数据检测失败: " + e.getMessage());
                        statusInfoLabel.setTextFill(Color.web("#e74c3c"));
                    });
                }
            }).start();
        });
    }

    /**
     * 供外部调用的刷新入口（点击"刷新"按钮或数据变更后）
     */
    public void refreshFromDatabase() {
        Platform.runLater(() -> {
            statusInfoLabel.setText("\uD83D\uDD04 正在刷新数据...");
            statusInfoLabel.setTextFill(Color.web("#7f8c8d"));
            // 重新加载当前选中的国家
            loadDataAndRender();
        });
    }

    /**
     * 确保数据库连接和 DAO 工厂可用
     */
    private void ensureDatabaseReady() {
        try {
            DaoFactory.getInstance();
        } catch (IllegalStateException e) {
            DataSource ds = DataSource.getInstance("bri.db");
            ds.initIfAbsent();
            DaoFactory.getInstance("bri.db");
        }
    }

    /** 加载数据 + 渲染的入口 */
    private void loadDataAndRender() {
        try {
            String code = resolveCountryCode();
            if (code == null) {
                if (!countryCombo.getItems().isEmpty()) {
                    String first = countryCombo.getItems().get(0);
                    code = extractCode(first);
                }
                if (code == null) code = "PAK";
            }

            List<CooperationIndex> data = loadFromDatabase(code);
            if (data == null || data.isEmpty()) {
                statusInfoLabel.setText("\u26A0 当前国家无数据库数据");
                statusInfoLabel.setTextFill(Color.web("#e74c3c"));
                currentDataRef.set(new ArrayList<>());
            } else {
                statusInfoLabel.setText("\u2705 数据库数据 (" + data.size() + " 个月)");
                statusInfoLabel.setTextFill(Color.web("#27ae60"));
                currentDataRef.set(data);
            }
        } catch (Exception e) {
            System.err.println("[TrendChartPanel] loadDataAndRender 异常: " + e.getMessage());
            e.printStackTrace();
            currentDataRef.set(new ArrayList<>());
            statusInfoLabel.setText("\u274C 加载数据异常: " + e.getMessage());
            statusInfoLabel.setTextFill(Color.web("#e74c3c"));
        }

        // 重绘
        redrawChart();
        updateStats();
    }

    // ==================== 数据库加载 ====================

    /**
     * 直接从数据库查询中国与指定国家之间的月度双边趋势数据
     */
    private List<CooperationIndex> loadFromDatabase(String code) {
        if (code == null || code.isEmpty()) return Collections.emptyList();
        try {
            ensureDatabaseReady();
            DaoFactory df = DaoFactory.getInstance();
            EventDao eventDao = df.eventDao();

            LocalDate[] dateRange;
            try {
                dateRange = eventDao.getDateRange();
            } catch (Exception e) {
                dateRange = new LocalDate[]{com.yourteam.bri.service.DataService.getActiveFrom(), com.yourteam.bri.service.DataService.getActiveTo()};
            }
            LocalDate from = com.yourteam.bri.service.DataService.getActiveFrom();
            LocalDate to = com.yourteam.bri.service.DataService.getActiveTo();

            List<MonthlyTrend> trends = eventDao.monthlyBilateralTrend(
                code, from.toString(), to.toString());

            if (trends == null || trends.isEmpty()) return Collections.emptyList();

            String name = BRICountries.getCountryName(code);
            if (name == null || name.isEmpty()) name = code;

            List<CooperationIndex> result = new ArrayList<>();
            for (MonthlyTrend mt : trends) {
                CooperationIndex ci = new CooperationIndex();
                ci.setCountryCode(code);
                ci.setCountryName(name);
                ci.setMonth(mt.getMonth());
                ci.setIndexValue(Math.max(0, Math.min(1.0, mt.getCoopRatio())));
                ci.setAvgGoldstein(Math.max(-1, Math.min(1.0, mt.getAvgGoldstein() / 10.0)));
                ci.setAvgTone(Math.max(-1, Math.min(1.0, mt.getAvgTone() / 10.0)));
                ci.setEventCount(mt.getEventCount());
                result.add(ci);
            }

            result.sort(Comparator.comparing(CooperationIndex::getMonth));
            return result;
        } catch (Exception e) {
            System.err.println("[TrendChartPanel] loadFromDatabase 失败: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // ==================== 创建组件 ====================

    private HBox createControlBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label selectLabel = new Label("选择国家:");
        selectLabel.setFont(Font.font("Microsoft YaHei", 13));

        countryCombo = new ComboBox<>();
        countryCombo.setPrefWidth(200);
        countryCombo.setEditable(false);

        // 国家切换（列表将在后台检测数据后填充）
        countryCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                loadDataAndRender();
            }
        });

        filteredInfoLabel = new Label("正在检测各国数据...");

        Button updateBtn = new Button("\uD83D\uDD04 更新趋势");
        updateBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-padding: 7px 16px; -fx-background-radius: 5; -fx-cursor: hand;");
        updateBtn.setOnAction(e -> loadDataAndRender());

        statusInfoLabel = new Label("正在加载趋势数据...");
        statusInfoLabel.setFont(Font.font("Microsoft YaHei", 10));
        statusInfoLabel.setTextFill(Color.web("#7f8c8d"));

        filteredInfoLabel.setFont(Font.font("Microsoft YaHei", 9));
        filteredInfoLabel.setTextFill(Color.web("#95a5a6"));

        bar.getChildren().addAll(selectLabel, countryCombo, updateBtn, statusInfoLabel, filteredInfoLabel);
        return bar;
    }

    private VBox createStatsBox() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");
        box.setPrefHeight(80);

        Label statsTitle = new Label("\uD83D\uDCCA 统计分析摘要");
        statsTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        HBox row = new HBox(24);
        row.setAlignment(Pos.CENTER_LEFT);
        avgIndexLabel = new Label("平均合作指数: --");
        trendLabel = new Label("趋势方向: --");
        peakLabel = new Label("峰值月份: --");
        valleyLabel = new Label("谷值月份: --");

        Font f = Font.font("Microsoft YaHei", 12);
        for (Label lb : new Label[]{avgIndexLabel, trendLabel, peakLabel, valleyLabel}) {
            lb.setFont(f);
        }

        row.getChildren().addAll(avgIndexLabel, trendLabel, peakLabel, valleyLabel);
        box.getChildren().addAll(statsTitle, row);
        return box;
    }

    // ==================== 绘图 ====================

    /**
     * 绘制空图表（无数据时的坐标系框架）
     */
    private void drawEmptyChart(GraphicsContext gc, double w, double h) {
        double L = 60, R = 50, T = 40, B = 50;
        double cw = w - L - R;
        double ch = h - T - B;

        if (cw < 60 || ch < 60) {
            gc.setFill(Color.rgb(150, 150, 150));
            gc.setFont(new Font("Microsoft YaHei", 14));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("画布尺寸过小", w / 2, h / 2);
            return;
        }

        // 标题
        gc.setFill(Color.rgb(30, 30, 30));
        gc.setFont(new Font(14));
        gc.setTextAlign(TextAlignment.CENTER);
        String countryName = "";
        try {
            String sel = countryCombo.getValue();
            if (sel != null && sel.contains("(")) {
                countryName = sel.substring(0, sel.indexOf("(")).trim();
            }
        } catch (Exception ignored) {}
        gc.fillText("中国 \u2014 " + countryName + " 合作趋势", w / 2, 22);

        // 网格
        gc.setStroke(Color.rgb(220, 222, 225));
        gc.setLineWidth(0.5);
        for (int i = 0; i <= 5; i++) {
            double y = T + ch - (ch * i / 5);
            gc.strokeLine(L, y, L + cw, y);
            gc.setFill(Color.rgb(100, 100, 100));
            gc.setFont(new Font(9));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.format("%.2f", 1.0 * i / 5), L - 5, y + 3);
        }

        // 右Y轴刻度
        for (int i = 0; i <= 5; i++) {
            double y = T + ch - (ch * i / 5);
            gc.setFill(Color.rgb(130, 130, 130));
            gc.setFont(new Font(8));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.1f", -1.0 + 2.0 * i / 5), L + cw + 4, y + 3);
        }

        // 坐标轴线
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(1.5);
        gc.strokeLine(L, T + ch, L + cw, T + ch);
        gc.strokeLine(L, T, L, T + ch);

        // 左侧标签
        gc.setFill(Color.DODGERBLUE);
        gc.setFont(new Font(9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("合作指数", 22, T + ch / 2);

        // 右侧标签
        gc.setFill(Color.ORANGERED);
        gc.setFont(new Font(8));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("G/T", w - 12, T + ch / 2);

        // X轴月份标签（占位）
        gc.setFill(Color.rgb(80, 80, 80));
        gc.setFont(new Font(8));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i <= 6; i++) {
            double x = L + (double) i / 6 * cw;
            gc.fillText("--", x, T + ch + 14);
        }

        // 中间提示文字
        gc.setFill(Color.rgb(180, 180, 180));
        gc.setFont(new Font("Microsoft YaHei", 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("暂无趋势数据\n请先加载数据文件", w / 2, h / 2);

        // 图例占位
        String[][] legs = {{"\u5408\u4F5C\u6307\u6570", "#3498db"},
                           {"Goldstein\u5747\u503C", "#e74c3c"},
                           {"\u60C5\u611F\u8BED\u8C03", "#2ecc71"}};
        double lx = L + 12, ly = T + 8;
        gc.setFont(new Font(10));
        for (String[] leg : legs) {
            Color c = Color.web(leg[1]);
            gc.setFill(c);
            gc.fillRect(lx, ly, 11, 11);
            gc.setFill(Color.rgb(50, 50, 50));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(leg[0], lx + 15, ly + 10);
            lx += 100;
            if (lx > L + cw - 40) {
                lx = L + 12;
                ly += 18;
            }
        }
    }

    private void redrawChart() {
        try {
            GraphicsContext gc = trendCanvas.getGraphicsContext2D();
            double w = trendCanvas.getWidth();
            double h = trendCanvas.getHeight();

            gc.clearRect(0, 0, w, h);

            if (w < 80 || h < 80) {
                gc.setFill(Color.GRAY);
                gc.setFont(new Font(12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("画布尺寸过小，请调整窗口大小", Math.max(w / 2, 40), Math.max(h / 2, 40));
                return;
            }

            List<CooperationIndex> currentData = currentDataRef.get();
            if (currentData == null || currentData.isEmpty()) {
                drawEmptyChart(gc, w, h);
                return;
            }

            int n = currentData.size();
            double L = 60, R = 50, T = 40, B = 50;
            double cw = w - L - R;
            double ch = h - T - B;

            if (cw < 60 || ch < 60) {
                double L2 = 40, R2 = 20, T2 = 20, B2 = 30;
                cw = w - L2 - R2;
                ch = h - T2 - B2;
                if (cw < 60 || ch < 60) {
                    gc.setFill(Color.rgb(180, 180, 180));
                    gc.setFont(new Font("Microsoft YaHei", 16));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText("窗口太小无法绘制趋势图", w / 2, h / 2);
                    return;
                }
                L = L2;
                R = R2;
                T = T2;
                B = B2;
            }

            // 标题
            String countryName = currentData.get(0).getCountryName();
            gc.setFill(Color.rgb(30, 30, 30));
            gc.setFont(new Font(14));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("中国 \u2014 " + (countryName != null ? countryName : "") + " 合作趋势", w / 2, 22);

            double[] idxV = currentData.stream().mapToDouble(CooperationIndex::getIndexValue).toArray();
            double[] gsV = currentData.stream().mapToDouble(CooperationIndex::getAvgGoldstein).toArray();
            double[] tv = currentData.stream().mapToDouble(CooperationIndex::getAvgTone).toArray();

            // Y 轴范围（合作指数）
            double yMin = 0;
            double arrMax = Arrays.stream(idxV).max().orElse(1.0);
            double yMax = Math.max(1.0, Math.ceil(arrMax * 10) / 10 * 1.2);

            // 右 Y 轴范围（Goldstein / Tone）
            double rMin, rMax;
            double gsAbsMax = Math.max(
                Arrays.stream(gsV).map(Math::abs).max().orElse(1.0),
                Arrays.stream(tv).map(Math::abs).max().orElse(1.0)
            );
            if (gsAbsMax > 1.2) {
                rMin = -2.0;
                rMax = 2.0;
            } else if (gsAbsMax > 0.6) {
                rMin = -1.5;
                rMax = 1.5;
            } else {
                rMin = -1.0;
                rMax = 1.0;
            }

            double yRange = yMax - yMin;
            double rRange = rMax - rMin;

            // 网格 + 左 Y 轴
            gc.setStroke(Color.rgb(220, 222, 225));
            gc.setLineWidth(0.5);
            for (int i = 0; i <= 5; i++) {
                double y = T + ch - (ch * i / 5);
                gc.strokeLine(L, y, L + cw, y);
                gc.setFill(Color.rgb(100, 100, 100));
                gc.setFont(new Font(9));
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(String.format("%.2f", yMin + yRange * i / 5), L - 5, y + 3);
            }
            gc.setFill(Color.DODGERBLUE);
            gc.setFont(new Font(9));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("合作指数", 22, T + ch / 2);

            // 右 Y 轴
            for (int i = 0; i <= 5; i++) {
                double y = T + ch - (ch * i / 5);
                gc.setFill(Color.rgb(130, 130, 130));
                gc.setFont(new Font(8));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(String.format("%.1f", rMin + rRange * i / 5), L + cw + 4, y + 3);
            }
            gc.setFill(Color.ORANGERED);
            gc.setFont(new Font(8));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("G/T", w - 12, T + ch / 2);

            // 坐标轴线
            gc.setStroke(Color.rgb(80, 80, 80));
            gc.setLineWidth(1.5);
            gc.strokeLine(L, T + ch, L + cw, T + ch);
            gc.strokeLine(L, T, L, T + ch);

            // X 轴月份标签
            int step = Math.max(1, n / 10);
            gc.setFill(Color.rgb(80, 80, 80));
            gc.setFont(new Font(8));
            gc.setTextAlign(TextAlignment.CENTER);
            double xScale = (n > 1) ? (double) (n - 1) : 1.0;
            for (int i = 0; i < n; i += step) {
                double x = L + (double) i / xScale * cw;
                String lb = currentData.get(i).getMonth();
                if (lb != null && lb.length() > 7) {
                    lb = lb.substring(5);
                }
                gc.fillText(lb != null ? lb : "", x, T + ch + 14);
            }

            // 三条折线
            plotLine(gc, L, cw, T, ch, idxV, yMin, yRange, Color.DODGERBLUE, 2.5);
            plotLine(gc, L, cw, T, ch, gsV, rMin, rRange, Color.ORANGERED, 1.8);
            plotLine(gc, L, cw, T, ch, tv, rMin, rRange, Color.LIMEGREEN, 1.8);

            // 图例
            String[][] legs = {{"\u5408\u4F5C\u6307\u6570", "#3498db"},
                               {"Goldstein\u5747\u503C", "#e74c3c"},
                               {"\u60C5\u611F\u8BED\u8C03", "#2ecc71"}};
            double lx = L + 12, ly = T + 8;
            gc.setFont(new Font(10));
            for (String[] leg : legs) {
                Color c = Color.web(leg[1]);
                gc.setFill(c);
                gc.fillRect(lx, ly, 11, 11);
                gc.setFill(Color.rgb(50, 50, 50));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(leg[0], lx + 15, ly + 10);
                lx += 100;
                if (lx > L + cw - 40) {
                    lx = L + 12;
                    ly += 18;
                }
            }
        } catch (Exception e) {
            System.err.println("[TrendChartPanel] redrawChart 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void plotLine(GraphicsContext gc, double L, double cw,
                          double T, double ch, double[] vals,
                          double yMin, double yRange, Color color, double lw) {
        int n = vals.length;
        if (n < 1) return;

        double xScale = (n > 1) ? (double) (n - 1) : 1.0;

        gc.setStroke(color);
        gc.setLineWidth(lw);

        double px = L;
        double py = T + ch - ((vals[0] - yMin) / yRange) * ch;
        gc.setFill(color);
        gc.fillOval(px - 3, py - 3, 6, 6);

        if (n < 2) return; // 只有一个数据点，只画点

        for (int i = 1; i < n; i++) {
            double v = vals[i];
            if (Double.isNaN(v)) continue;
            double x = L + (double) i / xScale * cw;
            double y = T + ch - ((v - yMin) / yRange) * ch;
            gc.strokeLine(px, py, x, y);
            gc.setFill(color);
            gc.fillOval(x - 3, y - 3, 6, 6);
            px = x;
            py = y;
        }
    }

    // ==================== 统计摘要 ====================

    private void updateStats() {
        List<CooperationIndex> currentData = currentDataRef.get();
        if (currentData == null || currentData.isEmpty()) {
            avgIndexLabel.setText("平均合作指数: --");
            trendLabel.setText("趋势方向: --");
            peakLabel.setText("峰值月份: --");
            valleyLabel.setText("谷值月份: --");
            return;
        }
        double avg = currentData.stream()
            .mapToDouble(CooperationIndex::getIndexValue).average().orElse(0);
        avgIndexLabel.setText(String.format("平均合作指数: %.3f", avg));

        if (currentData.size() >= 2) {
            double d = currentData.get(currentData.size() - 1).getIndexValue()
                     - currentData.get(0).getIndexValue();
            String dir = d > 0.03 ? "\u2191 上升" : d < -0.03 ? "\u2193 下降" : "\u2192 平稳";
            trendLabel.setText("趋势方向: " + dir + " (" + String.format("%+.3f", d) + ")");
        }
        CooperationIndex peak = currentData.stream()
            .max(Comparator.comparingDouble(CooperationIndex::getIndexValue)).orElse(null);
        CooperationIndex valley = currentData.stream()
            .min(Comparator.comparingDouble(CooperationIndex::getIndexValue)).orElse(null);
        if (peak != null && peak.getMonth() != null) {
            peakLabel.setText(String.format("峰值: %s (%.3f)", peak.getMonth(), peak.getIndexValue()));
        }
        if (valley != null && valley.getMonth() != null) {
            valleyLabel.setText(String.format("谷值: %s (%.3f)", valley.getMonth(), valley.getIndexValue()));
        }
    }

    // ==================== 工具 ====================

    /**
     * 从下拉框显示文本中提取三位国家代码
     */
    private String extractCode(String d) {
        if (d == null || d.trim().isEmpty()) return null;
        String text = d.trim();
        if (text.matches(".*\\([A-Z]{3}\\).*")) {
            return text.replaceAll(".*\\(([A-Z]{3})\\).*", "$1");
        }
        if (text.matches("[A-Za-z]{3}")) {
            return text.toUpperCase();
        }
        return null;
    }

    private String resolveCountryCode() {
        try {
            String sel = countryCombo.getValue();
            if (sel == null || sel.trim().isEmpty()) {
                if (!countryCombo.getItems().isEmpty()) {
                    sel = countryCombo.getItems().get(0);
                } else {
                    return null;
                }
            }
            return extractCode(sel);
        } catch (Exception e) {
            return null;
        }
    }
}
