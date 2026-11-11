package com.yourteam.bri.gui;

import com.yourteam.bri.model.TimeRange;
import com.yourteam.bri.service.DataService;
import com.yourteam.bri.util.BRICountries;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.bri.engine.BriAnalysisEngine;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.yourteam.bri.service.BriEngineHolder;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class MainWindow extends Application implements com.yourteam.bri.service.DataChangeListener {
    private BorderPane root;
    private VBox mainContentArea;
        private NewsPanel newsPanel;
    private TimelinePanel timelinePanel;
    private QueryPanel queryPanel;
    private TrendChartPanel trendChartPanel;
        private AprioriPanel aprioriPanel;
    private ClusteringPanel clusteringPanel;
    private MapPanel mapPanel;
    private ReportPanel reportPanel;
    private StackPane contentStack;           // 中央内容区（地图/图表等）
    private Label statusLabel;
    private Label dashboardStatusLabel;
    private String currentModule = "dashboard"; // 当前显示模块，用于数据变更后重建

        /** 用户设定的固定导出目录，如果为 null 则每次弹出选择 */
    private String exportDir = null;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("一带一路合作态势分析系统 - B&R Cooperation Analysis System");
        primaryStage.setWidth(1400);
        primaryStage.setHeight(900);

        root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f7fa;");

        // 顶部标题栏 + 工具栏
        root.setTop(createTopBar());

        // ===== 中央：地图/图表区域（左中）+ 新闻面板（右）=====
        HBox centerContent = new HBox(10);
        centerContent.setPadding(new Insets(8, 12, 4, 12));
        VBox.setVgrow(centerContent, Priority.ALWAYS);

        // 左中：内容切换区（地图、仪表盘、排名等切换显示）
        VBox leftCenter = new VBox(8);
        leftCenter.setVgrow(leftCenter, Priority.ALWAYS);
        HBox.setHgrow(leftCenter, Priority.ALWAYS);

        // 左侧导航菜单
        HBox navAndContent = new HBox(8);
        VBox.setVgrow(navAndContent, Priority.ALWAYS);

        // 导航栏
        VBox sidebar = createSidebar();

        // 中央内容堆栈
        contentStack = new StackPane();
        contentStack.setPadding(new Insets(5));
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        navAndContent.getChildren().addAll(sidebar, contentStack);
        leftCenter.getChildren().add(navAndContent);

        // 右侧：新闻面板
        newsPanel = new NewsPanel();

        centerContent.getChildren().addAll(leftCenter, newsPanel);

        // ===== 底部：时间轴 =====
        timelinePanel = new TimelinePanel();
                timelinePanel.setTimelineChangeListener(new TimelinePanel.TimelineChangeListener() {
                    @Override
                    public void onTimeRangeChanged(LocalDate start, LocalDate end) {
                        updateStatusBar("时间范围: " + start + " ~ " + end);
                        // 将时间范围同步到 DataService 全局范围，使所有查询以此为准
                        DataService.setActiveTimeRange(start, end);
                        // 只刷新当前显示的面板（其他面板切过去再刷新）
                        refreshOnlyCurrentPanel();
                    }

                        @Override
            public void onPlayStateChanged(boolean playing) {
                updateStatusBar(playing ? "\u25B6 播放中" : "\u23F8 暂停");
            }
        });

        // 主区域布局（中央 + 底部时间轴）
        VBox mainWithTimeline = new VBox(6);
        mainWithTimeline.setVgrow(mainWithTimeline, Priority.ALWAYS);
        mainWithTimeline.getChildren().addAll(centerContent, timelinePanel);
        VBox.setVgrow(centerContent, Priority.ALWAYS);

        root.setCenter(mainWithTimeline);

        // 底部状态栏
        root.setBottom(createStatusBar());

        // 默认显示仪表盘
        showDashboard();

        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setScene(scene);
        // 注册数据变更监听（数据导入后自动刷新当前面板）
        com.yourteam.bri.service.DataChangeNotifier.addListener(this);

        // 启动时检查是否有已导入的数据库，自动加载到引擎
        Platform.runLater(() -> {
            try {
                java.io.File dbFile = new java.io.File("bri.db");
                if (dbFile.exists() && dbFile.length() > 4096) {
                    updateStatusBar("⏳ 检测到已有数据库，正在加载历史数据...");
                    BriAnalysisEngine engine = BriEngineHolder.getEngine();
                    DaoFactory df = DaoFactory.getInstance("bri.db");
                    EventQuery q = new EventQuery.Builder()
                        .dateFrom("2018-01-01")
                        .dateTo(LocalDate.now().toString())
                        .limit(Integer.MAX_VALUE)
                        .build();
                    List<Event> events = df.eventDao().query(q);
                    if (events != null && !events.isEmpty()) {
                        engine.loadFromEvents(events);
                        updateStatusBar("✅ 已加载 " + events.size() + " 条历史数据");
                    } else {
                        updateStatusBar("✅ 数据库已就绪（无事件数据）");
                    }
                    refreshAll();
                } else {
                    updateStatusBar("✅ 系统就绪 | 数据源: GDELT 真实数据");
                }
            } catch (Exception e) {
                System.out.println("[启动] 自动加载数据失败: " + e.getMessage());
                updateStatusBar("✅ 系统就绪 | 数据源: GDELT 真实数据");
            }
        });

        primaryStage.show();

        updateStatusBar("\u2705 系统就绪 | 数据源: GDELT 真实数据");

        // 窗口关闭时优雅关闭数据库连接池，确保数据持久化
        primaryStage.setOnCloseRequest(we -> {
            try {
                com.bri.dao.DaoFactory df = com.bri.dao.DaoFactory.getInstance();
                df.close();
            } catch (Exception ignored) {}
        });
    }

    /**
     * 数据变更回调 — 当新数据导入完成后，在当前面板上显示刷新提示。
     */
    @Override
    public void onDataChanged() {
        Platform.runLater(() -> {
            updateStatusBar("\uD83D\uDD04 新数据已加载，已同步更新显示");
            // 刷新当前显示的内容
            refreshAll();
        });
    }

    /**
     * 创建顶部标题栏 + 工具栏
     * <p>
     * 包含两行布局：
     * <ul>
     *   <li>第一行（标题行）：系统名称 + 右侧帮助/设置按钮 + 版本号</li>
     *   <li>第二行（工具行）：加载数据 / 刷新 / 导出按钮 + 当前模块指示</li>
     * </ul>
     * 标题栏使用蓝色渐变背景，工具行使用浅灰色背景。
     * </p>
     *
     * @return 顶部栏 VBox
     */
    private VBox createTopBar() {
        VBox topBar = new VBox(0);

        // 标题行
        HBox titleRow = new HBox(15);
        titleRow.setPadding(new Insets(10, 20, 10, 20));
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setStyle("-fx-background-color: linear-gradient(to right, #1a5276, #2980b9);");

        Label titleLabel = new Label("\uD83C\uDF0F 一带一路合作态势分析系统");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setStyle("-fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 工具栏右侧按钮
        HBox toolbarBtns = new HBox(8);
        toolbarBtns.setAlignment(Pos.CENTER_RIGHT);

        Button helpBtn = new Button("\u2753 帮助");
        helpBtn.setStyle("-fx-font-size: 11px; -fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 12;");
        helpBtn.setOnAction(e -> showHelpDialog());

        Button settingBtn = new Button("\u2699 设置");
        settingBtn.setStyle("-fx-font-size: 11px; -fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 12;");
        settingBtn.setOnAction(e -> showSettingDialog());

        Label versionLabel = new Label("v1.0 | GDELT 2.0");
        versionLabel.setFont(Font.font("Microsoft YaHei", 10));
        versionLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7);");

        toolbarBtns.getChildren().addAll(versionLabel, helpBtn, settingBtn);
        titleRow.getChildren().addAll(titleLabel, spacer, toolbarBtns);

        // 工具栏行
        HBox toolRow = new HBox(10);
        toolRow.setPadding(new Insets(6, 20, 6, 20));
        toolRow.setAlignment(Pos.CENTER_LEFT);
        toolRow.setStyle("-fx-background-color: #f0f4f8; -fx-border-color: #dde; -fx-border-width: 0 0 1 0;");

        Button loadBtn = new Button("\uD83D\uDCC2 加载数据");
        loadBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-cursor: hand;");
        loadBtn.setOnAction(e -> {
            showDataImport();
            updateStatusBar("数据加载中...");
        });

        Button refreshBtn = new Button("\uD83D\uDD04 刷新");
        refreshBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> {
            refreshAll();
            updateStatusBar("\uD83D\uDD04 数据已刷新");
        });

        Button exportBtn = new Button("\uD83D\uDCE4 导出");
        exportBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-cursor: hand;");
        exportBtn.setOnAction(e -> showExportDialog());

        Button initBtn = new Button("\uD83D\uDDD1 初始化");
        initBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-cursor: hand;");
        initBtn.setOnAction(e -> resetDatabase());

        Region toolSpacer = new Region();
        HBox.setHgrow(toolSpacer, Priority.ALWAYS);

        dashboardStatusLabel = new Label("当前模块: 仪表盘");
        dashboardStatusLabel.setFont(Font.font("Microsoft YaHei", 10));
        dashboardStatusLabel.setTextFill(Color.web("#64748b"));

        toolRow.getChildren().addAll(loadBtn, refreshBtn, exportBtn, initBtn, toolSpacer, dashboardStatusLabel);

        topBar.getChildren().addAll(titleRow, toolRow);
        return topBar;
    }

    /**
     * 创建左侧导航菜单栏
     * <p>
     * 深蓝色（#2c3e50）背景的垂直按钮列表，包含 8 个功能模块入口：
     * 总览仪表盘 / 数据管理 / 事件查询 / 趋势分析 / 指数排名 / 专题地图 / 区域雷达图 / 聚类与风险
     * </p>
     * <p>
     * 点击按钮时自动切换 contentStack 内容，高亮当前选中按钮（蓝色背景），
     * 并更新 dashboardStatusLabel 显示的模块名称。
     * 按钮具有鼠标悬停高亮效果（浅深色背景）。
     * </p>
     *
     * @return 导航栏 VBox
     */
    private VBox createSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPadding(new Insets(10, 6, 10, 6));
        sidebar.setPrefWidth(170);
        sidebar.setMinWidth(150);
        sidebar.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 8;");

        Label menuTitle = new Label("功能导航");
        menuTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 11));
        menuTitle.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-padding: 0 0 5 8;");

                                Button[] menuBtns = {
            createMenuButton("\uD83D\uDCCA 总览仪表盘", "dashboard"),
            createMenuButton("\uD83D\uDCC2 数据管理", "data"),
            createMenuButton("\uD83D\uDD0D 事件查询", "query"),
            createMenuButton("\uD83D\uDCC8 趋势分析", "trend"),
            createMenuButton("\uD83C\uDFC6 指数排名", "ranking"),
            createMenuButton("\uD83D\uDDFA 专题地图", "map"),
            createMenuButton("\uD83D\uDCE1 区域雷达图", "radar"),
            createMenuButton("\uD83C\uDFAF 聚类与风险", "clustering"),
            createMenuButton("\uD83D\uDD17 关联规则", "apriori"),
            createMenuButton("\uD83D\uDCC4 年度报告", "report")
        };

        sidebar.getChildren().add(menuTitle);
        sidebar.getChildren().addAll(menuBtns);
        return sidebar;
    }

    /**
     * 创建导航菜单按钮
     * <p>
     * 按钮默认透明背景白色文字，鼠标悬停变为 #34495e 背景，
     * 点击选中后变为 #2980b9（蓝色）背景。按钮关联 target 标识对应的模块，
     * 点击时调用对应的 showXxx() 方法切换页面。
     * </p>
     *
     * @param text   按钮显示文本（如 "\uD83D\uDCCA 总览仪表盘"）
     * @param target 模块标识（"dashboard"/"data"/"query"/"trend"/"ranking"/"map"/"radar"/"clustering"）
     * @return 配置完全的导航按钮
     */
    private Button createMenuButton(String text, String target) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(36);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 0, 0, 10));
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                     "-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei'; " +
                     "-fx-cursor: hand; -fx-background-radius: 5;");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; " +
             "-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei'; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 0 0 0 10;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
             "-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei'; -fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 0 0 0 10;"));

        btn.setOnAction(e -> {
            // 重置所有按钮样式
            for (javafx.scene.Node node : ((VBox)btn.getParent()).getChildren()) {
                if (node instanceof Button) {
                    node.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                        "-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei'; " +
                        "-fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 0 0 0 10;");
                }
            }
            // 高亮当前按钮
            btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-font-family: 'Microsoft YaHei'; " +
                "-fx-cursor: hand; -fx-background-radius: 5; -fx-padding: 0 0 0 10;");

            String moduleName = "";
            switch (target) {
                case "dashboard": showDashboard(); moduleName = "仪表盘"; break;
                case "data": showDataImport(); moduleName = "数据管理"; break;
                case "query": showQuery(); moduleName = "事件查询"; break;
                case "trend": showTrend(); moduleName = "趋势分析"; break;
                case "ranking": showRanking(); moduleName = "指数排名"; break;
                case "map": showMap(); moduleName = "专题地图"; break;
                case "radar": showRadar(); moduleName = "区域雷达图"; break;
                case "clustering": showClustering(); moduleName = "聚类与风险"; break;
                case "apriori": showApriori(); moduleName = "关联规则"; break;
                case "report": showReport(); moduleName = "年度报告"; break;
            }
            if (dashboardStatusLabel != null) {
                dashboardStatusLabel.setText("当前模块: " + moduleName);
            }
        });
        return btn;
    }

    /**
     * 创建底部状态栏
     * <p>
     * 浅灰色背景，包含：
     * <ul>
     *   <li>系统状态消息（如"\u2705 系统就绪"）</li>
     *   <li>统计信息：国家数 / 事件总量 / 平均指数 / 风险预警数</li>
     *   <li>当前时间（实时更新时间戳）</li>
     * </ul>
     * </p>
     *
     * @return 状态栏 HBox
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(5, 20, 5, 20));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("\u2705 系统就绪");
        statusLabel.setFont(Font.font("Microsoft YaHei", 11));

        // 统计信息
        Label statCountries = new Label("🇺🇳 国家: 40+");
        statCountries.setFont(Font.font("Microsoft YaHei", 10));
        statCountries.setTextFill(Color.web("#64748b"));
  
        Label statEvents = new Label("\uD83D\uDCCA 事件: 16,000+");
        statEvents.setFont(Font.font("Microsoft YaHei", 10));
        statEvents.setTextFill(Color.web("#64748b"));
  
        Label statAvgIndex = new Label("\uD83D\uDCC8 平均指数: 0.52");
        statAvgIndex.setFont(Font.font("Microsoft YaHei", 10));
        statAvgIndex.setTextFill(Color.web("#64748b"));
  
        Label statRisks = new Label("\u26A0 风险预警: 5条");
        statRisks.setFont(Font.font("Microsoft YaHei", 10));
        statRisks.setTextFill(Color.web("#e74c3c"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        timeLabel.setFont(Font.font("Microsoft YaHei", 10));
        timeLabel.setStyle("-fx-text-fill: #7f8c8d;");

        statusBar.getChildren().addAll(statusLabel, new Separator(javafx.geometry.Orientation.VERTICAL),
            statCountries, statEvents, statAvgIndex, statRisks, spacer, timeLabel);
        return statusBar;
    }

    // ==================== 页面切换 ====================

    private void showDashboard() {
        currentModule = "dashboard";
        contentStack.getChildren().clear();
        ScrollPane sp = new ScrollPane(new DashboardPanel());
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

                private void showDataImport() {
        currentModule = "data";
        contentStack.getChildren().clear();
        DataImportPanel dip = new DataImportPanel();
        dip.setExportDirSupplier(() -> exportDir);
        contentStack.getChildren().add(dip);
    }

        private void showQuery() {
        currentModule = "query";
        if (queryPanel == null) {
            queryPanel = new QueryPanel();
        }
        contentStack.getChildren().clear();
        contentStack.getChildren().add(queryPanel);
    }

                private void showTrend() {
        currentModule = "trend";
        contentStack.getChildren().clear();
        if (trendChartPanel == null) {
            trendChartPanel = new TrendChartPanel();
        } else {
            // 切换到趋势面板时刷新数据以匹配当前时间范围
            trendChartPanel.refreshFromDatabase();
        }
        ScrollPane sp = new ScrollPane(trendChartPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

    private void showRanking() {
        currentModule = "ranking";
        contentStack.getChildren().clear();
        ScrollPane sp = new ScrollPane(new RankingPanel());
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

        private void showMap() {
        currentModule = "map";
        if (mapPanel == null) {
            mapPanel = new MapPanel();
        }
        contentStack.getChildren().clear();
        ScrollPane sp = new ScrollPane(mapPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

    private void showRadar() {
        currentModule = "radar";
        contentStack.getChildren().clear();
        contentStack.getChildren().add(new RadarPanel());
    }

                private void showClustering() {
        currentModule = "clustering";
        if (clusteringPanel == null) {
            clusteringPanel = new ClusteringPanel();
        }
        contentStack.getChildren().clear();
        ScrollPane sp = new ScrollPane(clusteringPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

        private void showApriori() {
        currentModule = "apriori";
        if (aprioriPanel == null) {
            aprioriPanel = new AprioriPanel();
        }
        contentStack.getChildren().clear();
        ScrollPane sp = new ScrollPane(aprioriPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
        contentStack.getChildren().add(sp);
    }

        private void showReport() {
            currentModule = "report";
            if (reportPanel == null) {
                reportPanel = new ReportPanel();
            }
            contentStack.getChildren().clear();
            ScrollPane sp = new ScrollPane(reportPanel);
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");
            contentStack.getChildren().add(sp);
        }

    /**
     * 只刷新当前显示的面板（时间轴拖动时调用，不刷新其他面板）
     */
    private void refreshOnlyCurrentPanel() {
        if (currentModule == null) return;
        switch (currentModule) {
            case "trend":
                if (trendChartPanel != null) trendChartPanel.refreshFromDatabase();
                break;
            case "query":
                // QueryPanel 由用户手动触发查询，不自动刷新
                break;
                        case "apriori":
                            // AprioriPanel 保持状态（时间轴变化时不自动挖掘）
                            break;
            case "report":
                // ReportPanel 保持状态
                break;
            default:
                // 重建当前面板
                showCurrentModule();
                break;
        }
    }

    /**
     * 重新显示当前模块（重新创建面板以反映最新时间范围）
     */
    private void showCurrentModule() {
        if (currentModule == null) return;
        switch (currentModule) {
            case "dashboard": showDashboard(); break;
            case "data":      showDataImport(); break;
            case "query":     showQuery(); break;
            case "trend":     showTrend(); break;
            case "ranking":   showRanking(); break;
            case "map":       showMap(); break;
            case "radar":     showRadar(); break;
            case "clustering": showClustering(); break;
            case "apriori":   showApriori(); break;
            case "report":    showReport(); break;
        }
    }

    /**
     * 刷新所有数据 - 重新创建当前模块面板（从数据源重新获取数据）+ 刷新新闻面板
     * <p>
     * 数据变更后（如新文件导入完成），调用此方法重建面板以显示最新数据。
     * 各面板的构造函数中会调用 DataService / BriEngineHolder 获取最新数据。
     * </p>
     */
            private void refreshAll() {
        // 刷新新闻面板
        newsPanel.refreshNews();

        // 如果当前在 trend 模块，只刷新趋势数据而不重建面板
        if ("trend".equals(currentModule) && trendChartPanel != null) {
            trendChartPanel.refreshFromDatabase();
            return;
        }

                // 数据变更后，除事件查询、趋势图、关联规则和年度报告外其他模块重建（保留用户状态）
                if (currentModule != null && !"query".equals(currentModule) && !"apriori".equals(currentModule) && !"report".equals(currentModule)) {
            switch (currentModule) {
                case "dashboard": showDashboard(); break;
                case "data":      showDataImport(); break;
                case "ranking":   showRanking(); break;
                case "map":       showMap(); break;
                case "radar":     showRadar(); break;
                                case "clustering": showClustering(); break;
                case "apriori":   showApriori(); break;
            }
        }
    }

    /**
     * 重置数据库 — 删除所有已导入数据、清空引擎内存、刷新界面。
     * <p>
     * 弹出确认对话框警告用户此操作不可恢复，用户确认后才执行。
     * 初始化后界面回退到默认仪表盘。
     * </p>
     */
    private void resetDatabase() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("\u26A0 初始化数据库");
        confirm.setHeaderText("确定要初始化数据库吗？");
        confirm.setContentText(
            "此操作将：\n" +
            "• 删除数据库中所有已导入的 GDELT 事件数据\n" +
            "• 清空算法引擎内存中的所有索引和统计\n" +
            "• 新闻面板将变为空白\n\n" +
            "\u2757 此操作不可撤销，所有已导入的文件需要重新导入。\n" +
            "是否继续？"
        );

        ButtonType btnYes = new ButtonType("确认初始化", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnYes, btnCancel);

        confirm.showAndWait().ifPresent(response -> {
            if (response == btnYes) {
                try {
                    // 1. 关闭连接池
                    try {
                        com.bri.dao.DaoFactory df = com.bri.dao.DaoFactory.getInstance();
                        df.close();
                    } catch (Exception ignored) {}
                    try {
                        com.bri.config.DataSource ds = com.bri.config.DataSource.getInstance();
                        ds.close();
                    } catch (Exception ignored) {}

                    // 2. 删除数据库文件
                    java.io.File dbFile = new java.io.File("bri.db");
                    if (dbFile.exists()) dbFile.delete();
                    new java.io.File("bri.db-wal").delete();
                    new java.io.File("bri.db-shm").delete();

                    // 3. 重置引擎
                    com.yourteam.bri.service.BriEngineHolder.resetEngine();

                    // 4. 刷新界面
                    updateStatusBar("\uD83D\uDDD1️ 数据库已初始化，所有数据已清除");
                    currentModule = "dashboard";
                    refreshAll();

                } catch (Exception ex) {
                    updateStatusBar("❌ 初始化失败: " + ex.getMessage());
                }
            }
        });
    }

    /**
     * 更新底部状态栏的消息文本
     *
     * @param message 要显示的状态消息（如 "\u2705 系统就绪"）
     */
    private void updateStatusBar(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

        // ==================== 导出 (F-04) ====================

        /**
         * 显示导出对话框 — 生成完整的分析报告
         * <p>
         * 支持三种导出格式（F-04 要求 CSV/Excel）：
         * <ul>
         *   <li><b>HTML 报告</b> — 美观的可视化报告，含卡片和表格</li>
         *   <li><b>CSV 数据文件</b> — 纯文本表格，可用 Excel/WPS 直接打开</li>
         *   <li><b>XLSX Excel 文件</b> — 多工作表 Excel 文件，每类数据一个工作表</li>
         * </ul>
         * 导出内容包含系统中所有分析模块的数据：
         * <ul>
         *   <li>概览仪表盘：总国家数、合作事件、冲突事件、合作占比、平均情感语调</li>
         *   <li>合作指数排名：国家代码、国家名称、区域、综合合作指数、事件数、冲突占比、Goldstein 均值、情感语调、媒体关注度</li>
         *   <li>风险预警列表：国家代码、国家名称、聚类标签、风险等级、风险评分</li>
         *   <li>地图数据：国家代码、国家名称、区域、合作指数、事件数</li>
         *   <li>区域雷达对比：六大区域在六维指标上的数据</li>
         * </ul>
         * </p>
         */
        @SuppressWarnings("unchecked")
        private void showExportDialog() {
            // ===== 弹出格式选择对话框 =====
            ChoiceDialog<String> formatDialog = new ChoiceDialog<>(
                "HTML 报告 (含样式)", "HTML 报告 (含样式)", "CSV 数据文件", "XLSX Excel 文件 (多工作表)"
            );
            formatDialog.setTitle("导出分析数据");
            formatDialog.setHeaderText("选择导出格式 (F-04)");
            formatDialog.setContentText("请选择导出格式：");
            formatDialog.showAndWait().ifPresentOrElse(format -> {
                // 1. 获取所有分析数据
                Map<String, Object> dashboardData = DataService.getDashboardData();
                List<com.yourteam.bri.model.CooperationIndex> rankings = DataService.getCooperationRankings();
                List<com.yourteam.bri.model.ClusterResult> clusters = DataService.getClusterResults();
                List<Map<String, Object>> mapData = DataService.getMapData();
                Map<String, double[]> regionData = DataService.getRegionComparisonData();
                String[] indicators = DataService.getRadarIndicators();

                String ext;
                String desc;
                if (format.contains("XLSX")) {
                    ext = ".xlsx";
                    desc = "Excel 文件 (*.xlsx)";
                } else if (format.contains("CSV")) {
                    ext = ".csv";
                    desc = "CSV 文件 (*.csv)";
                } else {
                    ext = ".html";
                    desc = "HTML 文件 (*.html)";
                }

                String defaultName = "bri_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ext;

                // 2. 确定文件路径
                java.io.File file;
                if (exportDir != null) {
                    file = new java.io.File(exportDir, defaultName);
                } else {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("导出分析数据");
                    fileChooser.setInitialFileName(defaultName);
                    fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter(desc, "*" + ext),
                        new FileChooser.ExtensionFilter("所有文件", "*.*")
                    );
                    file = fileChooser.showSaveDialog(null);
                    if (file == null) return;
                }

                // 3. 生成并写入
                try {
                    if (ext.equals(".xlsx")) {
                        buildAndWriteXlsx(file, dashboardData, rankings, clusters, mapData, regionData, indicators);
                    } else if (ext.equals(".csv")) {
                        String content = buildCsvReport(dashboardData, rankings, clusters, mapData);
                        java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        String content = buildHtmlReport(dashboardData, rankings, clusters, mapData, regionData, indicators);
                        java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                    }

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("导出成功");
                    alert.setHeaderText("✅ " + ext.toUpperCase() + " 格式导出完成");
                    alert.setContentText("分析报告已导出至：\n" + file.getAbsolutePath() +
                        "\n\n文件大小：" + String.format("%,d", file.length()) + " 字节");
                    alert.showAndWait();
                    updateStatusBar("✅ 分析报告已导出: " + file.getName() + " (" + format + ")");

                } catch (Exception ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("导出失败");
                    alert.setHeaderText(null);
                    alert.setContentText("导出时出错: " + ex.getMessage() +
                        "\n\n请检查磁盘空间或文件是否被占用。");
                    alert.showAndWait();
                    updateStatusBar("❌ 导出失败: " + ex.getMessage());
                }
            }, () -> {}); // 用户取消
        }

        // ==================== XLSX Excel 导出（多工作表）====================

        /**
         * 构建并写入 XLSX 文件（多工作表 Excel）
         * <p>
         * 包含 5 个工作表：概览仪表盘、合作指数排名、聚类风险分析、地图数据、区域雷达对比
         * </p>
         */
        @SuppressWarnings("unchecked")
        private void buildAndWriteXlsx(java.io.File file,
                                       Map<String, Object> dashboardData,
                                       List<com.yourteam.bri.model.CooperationIndex> rankings,
                                       List<com.yourteam.bri.model.ClusterResult> clusters,
                                       List<Map<String, Object>> mapData,
                                       Map<String, double[]> regionData,
                                       String[] indicators) throws Exception {
            try (var fos = new java.io.FileOutputStream(file);
                 var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

                // ---- 通用样式 ----
                var headerStyle = wb.createCellStyle();
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                var headerFont = wb.createFont();
                headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 11);
                headerStyle.setFont(headerFont);

                var dataStyle = wb.createCellStyle();
                dataStyle.setWrapText(true);

                // ===== Sheet 1: 概览仪表盘 =====
                org.apache.poi.ss.usermodel.Sheet sheet1 = wb.createSheet("概览仪表盘");
                String[][] overviewRows = {
                    {"指标", "值"},
                    {"沿线国家", safeStr(dashboardData.get("totalCountries"))},
                    {"合作事件", formatNum(safeInt(dashboardData.get("cooperationEvents")))},
                    {"冲突事件", formatNum(safeInt(dashboardData.get("conflictEvents")))},
                    {"合作占比", safeStr(dashboardData.get("cooperationRatio")) + "%"},
                    {"平均情感语调", String.format("%.1f", safeDouble(dashboardData.get("avgTone")))}
                };
                for (int i = 0; i < overviewRows.length; i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet1.createRow(i);
                    for (int j = 0; j < overviewRows[i].length; j++) {
                        var cell = row.createCell(j);
                        cell.setCellValue(overviewRows[i][j]);
                        if (i == 0) cell.setCellStyle(headerStyle);
                    }
                }
                sheet1.autoSizeColumn(0); sheet1.autoSizeColumn(1);

                // ===== Sheet 2: 合作指数排名 =====
                org.apache.poi.ss.usermodel.Sheet sheet2 = wb.createSheet("合作指数排名");
                String[] rankHeaders = {"排名", "国家代码", "国家名称", "区域", "综合合作指数",
                    "事件数", "冲突占比(%)", "Goldstein均值", "情感语调", "媒体关注度"};
                org.apache.poi.ss.usermodel.Row rankHeaderRow = sheet2.createRow(0);
                for (int j = 0; j < rankHeaders.length; j++) {
                    var cell = rankHeaderRow.createCell(j);
                    cell.setCellValue(rankHeaders[j]);
                    cell.setCellStyle(headerStyle);
                }
                if (rankings != null) {
                    for (int i = 0; i < rankings.size(); i++) {
                        var ci = rankings.get(i);
                        org.apache.poi.ss.usermodel.Row row = sheet2.createRow(i + 1);
                        row.createCell(0).setCellValue(i + 1);
                        row.createCell(1).setCellValue(ci.getCountryCode());
                        row.createCell(2).setCellValue(ci.getCountryName());
                        row.createCell(3).setCellValue(ci.getRegion() != null ? ci.getRegion() : "");
                        row.createCell(4).setCellValue(ci.getIndexValue());
                        row.createCell(5).setCellValue(ci.getEventCount());
                        row.createCell(6).setCellValue(Math.round(ci.getConflictRatio() * 10000.0) / 100.0);
                        row.createCell(7).setCellValue(ci.getAvgGoldstein());
                                                row.createCell(8).setCellValue(ci.getAvgTone());
                        row.createCell(9).setCellValue(ci.getMediaAttention());
                    }
                }
                for (int j = 0; j < rankHeaders.length; j++) sheet2.autoSizeColumn(j);

                // ===== Sheet 3: 聚类风险分析 =====
                org.apache.poi.ss.usermodel.Sheet sheet3 = wb.createSheet("聚类风险分析");
                String[] clusterHeaders = {"国家代码","国家名称","聚类标签","风险等级","风险评分"};
                var clRow = sheet3.createRow(0);
                for (int j = 0; j < clusterHeaders.length; j++) { var c = clRow.createCell(j); c.setCellValue(clusterHeaders[j]); c.setCellStyle(headerStyle); }
                if (clusters != null) {
                    for (int i = 0; i < clusters.size(); i++) {
                        var cr = clusters.get(i);
                        var row = sheet3.createRow(i + 1);
                        row.createCell(0).setCellValue(cr.getCountryCode());
                        row.createCell(1).setCellValue(cr.getCountryName());
                        row.createCell(2).setCellValue(cr.getClusterLabel());
                        row.createCell(3).setCellValue(cr.getRiskLevel());
                        row.createCell(4).setCellValue(cr.getRiskScore());
                    }
                }
                for (int j = 0; j < clusterHeaders.length; j++) sheet3.autoSizeColumn(j);

                // ===== Sheet 4: 地图数据 =====
                org.apache.poi.ss.usermodel.Sheet sheet4 = wb.createSheet("地图数据");
                String[] mapHeaders = {"国家代码","国家名称","区域","合作指数","事件数"};
                var mapHeaderRow = sheet4.createRow(0);
                for (int j = 0; j < mapHeaders.length; j++) { var c = mapHeaderRow.createCell(j); c.setCellValue(mapHeaders[j]); c.setCellStyle(headerStyle); }
                if (mapData != null) {
                    int r = 1;
                    for (var item : mapData) {
                        var row = sheet4.createRow(r++);
                        row.createCell(0).setCellValue((String) item.get("code"));
                        row.createCell(1).setCellValue((String) item.get("name"));
                        row.createCell(2).setCellValue((String) item.get("region"));
                        row.createCell(3).setCellValue((Double) item.get("index"));
                        row.createCell(4).setCellValue((Integer) item.get("eventCount"));
                    }
                }
                for (int j = 0; j < mapHeaders.length; j++) sheet4.autoSizeColumn(j);

                // ===== Sheet 5: 区域雷达对比 =====
                org.apache.poi.ss.usermodel.Sheet sheet5 = wb.createSheet("区域雷达对比");
                var radarHeaderRow = sheet5.createRow(0);
                var radarCell0 = radarHeaderRow.createCell(0); radarCell0.setCellValue("区域"); radarCell0.setCellStyle(headerStyle);
                for (int j = 0; j < indicators.length; j++) { var c = radarHeaderRow.createCell(j + 1); c.setCellValue(indicators[j]); c.setCellStyle(headerStyle); }
                int rowNum = 1;
                for (var entry : regionData.entrySet()) {
                    var row = sheet5.createRow(rowNum++);
                    row.createCell(0).setCellValue(entry.getKey());
                    double[] vals = entry.getValue();
                    for (int j = 0; j < indicators.length && j < vals.length; j++) {
                        row.createCell(j + 1).setCellValue(vals[j]);
                    }
                }
                sheet5.autoSizeColumn(0);

                wb.write(fos);
            }
        }

        // ==================== HTML 报告 ====================

        @SuppressWarnings("unchecked")
        private String buildHtmlReport(Map<String, Object> dashboardData,
                                       List<com.yourteam.bri.model.CooperationIndex> rankings,
                                       List<com.yourteam.bri.model.ClusterResult> clusters,
                                       List<Map<String, Object>> mapData,
                                       Map<String, double[]> regionData,
                                       String[] indicators) {
            StringBuilder sb = new StringBuilder();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n");
            sb.append("<title>一带一路合作态势分析报告</title>\n<style>\n");
            sb.append("body{font-family:'Microsoft YaHei','PingFang SC',sans-serif;margin:20px;background:#f5f7fa;}\n");
            sb.append(".container{max-width:1200px;margin:0 auto;background:white;padding:30px;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,0.1);}\n");
            sb.append("h1{color:#1a5276;text-align:center;border-bottom:3px solid #2980b9;padding-bottom:10px;}\n");
            sb.append("h2{color:#2c3e50;margin-top:30px;border-left:4px solid #2980b9;padding-left:10px;}\n");
            sb.append("h3{color:#34495e;margin-top:20px;}\n.meta{text-align:center;color:#7f8c8d;font-size:14px;margin-bottom:20px;}\n");
            sb.append("table{width:100%;border-collapse:collapse;margin:15px 0;}\n");
            sb.append("th{background:#2980b9;color:white;padding:8px 12px;text-align:left;font-size:13px;}\n");
            sb.append("td{padding:6px 12px;border:1px solid #e0e0e0;font-size:12px;}\n");
            sb.append("tr:nth-child(even){background:#f8f9fa;}\ntr:hover{background:#eaf2f8;}\n");
            sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:bold;}\n");
            sb.append(".badge-low{background:#27ae60;color:white;}.badge-mid{background:#f39c12;color:white;}.badge-high{background:#e74c3c;color:white;}\n");
            sb.append(".card-row{display:flex;gap:15px;flex-wrap:wrap;margin:15px 0;}\n");
            sb.append(".card{flex:1;min-width:150px;background:white;border:1px solid #e0e0e0;border-radius:8px;padding:15px;text-align:center;box-shadow:0 1px 4px rgba(0,0,0,0.05);}\n");
            sb.append(".card .value{font-size:24px;font-weight:bold;margin:5px 0;}\n.card .label{color:#7f8c8d;font-size:12px;}\n");
            sb.append(".section-desc{color:#666;font-size:13px;margin:5px 0 15px 0;}\n");
            sb.append(".footer{text-align:center;color:#95a5a6;font-size:11px;margin-top:30px;border-top:1px solid #eee;padding-top:15px;}\n");
            sb.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");

            sb.append("<h1>\uD83C\uDF0F 一带一路合作态势分析报告</h1>\n");
            sb.append("<div class=\"meta\">生成时间: ").append(now).append(" | 数据源: GDELT 2.0 真实数据</div>\n");

            // 1. 概览
            sb.append("<h2>\uD83D\uDCCA 一、概览仪表盘</h2>\n");
            sb.append("<div class=\"section-desc\">系统核心概览指标，反映一带一路沿线国家整体合作态势。</div>\n<div class=\"card-row\">\n");
            appendCard(sb, "\uD83C\uDF0D", "沿线国家", safeStr(dashboardData.get("totalCountries")), "#3498db");
            appendCard(sb, "\uD83E\uDD1D", "合作事件", formatNum(safeInt(dashboardData.get("cooperationEvents"))), "#2ecc71");
            appendCard(sb, "\u26A1", "冲突事件", formatNum(safeInt(dashboardData.get("conflictEvents"))), "#e74c3c");
            appendCard(sb, "\uD83D\uDCC8", "合作占比", safeStr(dashboardData.get("cooperationRatio")) + "%", "#9b59b6");
            appendCard(sb, "\uD83D\uDCAC", "平均情感", String.format("%.1f", safeDouble(dashboardData.get("avgTone"))), "#f39c12");
            sb.append("</div>\n");

            Object hotObj = dashboardData.get("hotTopics");
            if (hotObj instanceof List) {
                List<String[]> hotList = (List<String[]>) hotObj;
                if (!hotList.isEmpty()) {
                    sb.append("<h3>\uD83D\uDD25 合作热点 TOP5</h3>\n<table><tr><th>排名</th><th>国家代码</th><th>国家名称</th><th>事件数</th></tr>\n");
                    for (int i = 0; i < hotList.size(); i++) {
                        String[] item = hotList.get(i);
                        sb.append("<tr><td>").append(i + 1).append("</td><td>").append(item[0]).append("</td><td>").append(item[1]).append("</td><td>").append(item[2]).append("</td></tr>\n");
                    }
                    sb.append("</table>\n");
                }
            }
            Object riskObj = dashboardData.get("riskWarnings");
            if (riskObj instanceof List) {
                List<String[]> riskList = (List<String[]>) riskObj;
                if (!riskList.isEmpty()) {
                    sb.append("<h3>\u26A0 风险预警 TOP5</h3>\n<table><tr><th>排名</th><th>国家代码</th><th>国家名称</th><th>风险等级</th></tr>\n");
                    for (int i = 0; i < riskList.size(); i++) {
                        String[] item = riskList.get(i);
                        String cls = item[2].contains("高") ? "badge-high" : item[2].contains("中") ? "badge-mid" : "badge-low";
                        sb.append("<tr><td>").append(i + 1).append("</td><td>").append(item[0]).append("</td><td>").append(item[1]).append("</td><td><span class=\"badge ").append(cls).append("\">").append(item[2]).append("</span></td></tr>\n");
                    }
                    sb.append("</table>\n");
                }
            }

            // 2. 合作指数排名
            sb.append("<h2>\uD83C\uDFC6 二、合作指数排名</h2>\n");
            sb.append("<div class=\"section-desc\">基于综合合作指数（Goldstein 均值、情感语调、合作占比、媒体关注度归一化加权）排名。</div>\n");
            if (rankings != null && !rankings.isEmpty()) {
                sb.append("<table><tr><th>排名</th><th>国家代码</th><th>国家名称</th><th>区域</th><th>综合合作指数</th><th>事件数</th><th>冲突占比</th><th>Goldstein 均值</th><th>情感语调</th><th>媒体关注度</th></tr>\n");
                for (int i = 0; i < rankings.size(); i++) {
                    var ci = rankings.get(i);
                    sb.append("<tr><td>").append(i + 1).append("</td><td>").append(ci.getCountryCode()).append("</td><td>").append(ci.getCountryName()).append("</td><td>").append(ci.getRegion() != null ? ci.getRegion() : "").append("</td>");
                    sb.append("<td>").append(String.format("%.4f", ci.getIndexValue())).append("</td><td>").append(ci.getEventCount()).append("</td><td>").append(String.format("%.2f%%", ci.getConflictRatio() * 100)).append("</td>");
                    sb.append("<td>").append(String.format("%.2f", ci.getAvgGoldstein())).append("</td><td>").append(String.format("%.2f", ci.getAvgTone())).append("</td><td>").append(ci.getMediaAttention()).append("</td></tr>\n");
                }
                sb.append("</table>\n<p style=\"color:#666;font-size:12px;\">共 ").append(rankings.size()).append(" 个国家</p>\n");
            } else { sb.append("<p>暂无排名数据</p>\n"); }

            // 3. 聚类风险分析
            sb.append("<h2>\uD83C\uDFAF 三、聚类风险分析</h2>\n");
            sb.append("<div class=\"section-desc\">基于 K-Means 聚类将一带一路国家分为四类，按风险评分从高到低排序。</div>\n");
            if (clusters != null && !clusters.isEmpty()) {
                sb.append("<table><tr><th>排名</th><th>国家代码</th><th>国家名称</th><th>聚类标签</th><th>风险等级</th><th>风险评分</th></tr>\n");
                for (int i = 0; i < clusters.size(); i++) {
                    var cr = clusters.get(i);
                    String cls = cr.getRiskLevel().contains("高") ? "badge-high" : cr.getRiskLevel().contains("中") ? "badge-mid" : "badge-low";
                    sb.append("<tr><td>").append(i + 1).append("</td><td>").append(cr.getCountryCode()).append("</td><td>").append(cr.getCountryName()).append("</td>");
                    sb.append("<td>").append(cr.getClusterLabel()).append("</td><td><span class=\"badge ").append(cls).append("\">").append(cr.getRiskLevel()).append("</span></td><td>").append(String.format("%.1f", cr.getRiskScore())).append("</td></tr>\n");
                }
                sb.append("</table>\n<p style=\"color:#666;font-size:12px;\">共 ").append(clusters.size()).append(" 个国家的聚类数据</p>\n");
            } else { sb.append("<p>暂无聚类数据</p>\n"); }

            // 4. 地图数据
            sb.append("<h2>\uD83D\uDDFA 四、地图数据</h2>\n");
            sb.append("<div class=\"section-desc\">各国合作指数与事件数据，用于地理分布可视化展示。</div>\n");
            if (mapData != null && !mapData.isEmpty()) {
                sb.append("<table><tr><th>国家代码</th><th>国家名称</th><th>区域</th><th>合作指数</th><th>事件数</th></tr>\n");
                for (var item : mapData) {
                    sb.append("<tr><td>").append(item.get("code")).append("</td><td>").append(item.get("name")).append("</td><td>").append(item.get("region")).append("</td>");
                    sb.append("<td>").append(String.format("%.4f", item.get("index"))).append("</td><td>").append(item.get("eventCount")).append("</td></tr>\n");
                }
                sb.append("</table>\n");
            } else { sb.append("<p>暂无地图数据</p>\n"); }

            // 5. 区域雷达对比
            sb.append("<h2>\uD83D\uDCE1 五、区域雷达对比</h2>\n");
            sb.append("<div class=\"section-desc\">六大子区域在六维指标（");
            for (int i = 0; i < indicators.length; i++) { if (i > 0) sb.append("、"); sb.append(indicators[i]); }
            sb.append("）上的对比数据。</div>\n");
            if (regionData != null && !regionData.isEmpty()) {
                sb.append("<table><tr><th>区域</th>");
                for (String ind : indicators) sb.append("<th>").append(ind).append("</th>");
                sb.append("</tr>\n");
                for (var entry : regionData.entrySet()) {
                    sb.append("<tr><td><strong>").append(entry.getKey()).append("</strong></td>");
                    double[] vals = entry.getValue();
                    for (int i = 0; i < indicators.length && i < vals.length; i++) sb.append("<td>").append(String.format("%.2f", vals[i])).append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</table>\n");
            } else { sb.append("<p>暂无区域对比数据</p>\n"); }

            sb.append("<div class=\"footer\">本报告由一带一路合作态势分析系统自动生成 | GDELT 2.0 数据源 | ").append(now).append("</div>\n");
            sb.append("</div>\n</body>\n</html>");
            return sb.toString();
        }

        // ==================== CSV 报告 ====================

        @SuppressWarnings("unchecked")
        private String buildCsvReport(Map<String, Object> dashboardData,
                                      List<com.yourteam.bri.model.CooperationIndex> rankings,
                                      List<com.yourteam.bri.model.ClusterResult> clusters,
                                      List<Map<String, Object>> mapData) {
            StringBuilder sb = new StringBuilder();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            sb.append("# 一带一路合作态势分析数据报告\n");
            sb.append("# 生成时间: ").append(now).append("\n");
            sb.append("# 数据源: GDELT 2.0 真实数据\n\n");
            sb.append("=== 概览仪表盘 ===\n指标,值\n");
            sb.append("沿线国家,").append(safeStr(dashboardData.get("totalCountries"))).append("\n");
            sb.append("合作事件,").append(safeStr(dashboardData.get("cooperationEvents"))).append("\n");
            sb.append("冲突事件,").append(safeStr(dashboardData.get("conflictEvents"))).append("\n");
            sb.append("合作占比,").append(safeStr(dashboardData.get("cooperationRatio"))).append("%\n");
            sb.append("平均情感,").append(String.format("%.1f", safeDouble(dashboardData.get("avgTone")))).append("\n\n");
            sb.append("=== 合作指数排名 ===\n排名,国家代码,国家名称,区域,综合合作指数,事件数,冲突占比,Goldstein均值,情感语调,媒体关注度\n");
            if (rankings != null) {
                for (int i = 0; i < rankings.size(); i++) {
                    var ci = rankings.get(i);
                    sb.append(i + 1).append(",").append(ci.getCountryCode()).append(",").append(ci.getCountryName()).append(",");
                    sb.append(ci.getRegion() != null ? ci.getRegion() : "").append(",");
                    sb.append(String.format("%.4f", ci.getIndexValue())).append(",").append(ci.getEventCount()).append(",");
                    sb.append(String.format("%.2f%%", ci.getConflictRatio() * 100)).append(",");
                    sb.append(String.format("%.2f", ci.getAvgGoldstein())).append(",").append(String.format("%.2f", ci.getAvgTone())).append(",");
                    sb.append(ci.getMediaAttention()).append("\n");
                }
            }
            sb.append("\n=== 聚类风险分析 ===\n国家代码,国家名称,聚类标签,风险等级,风险评分\n");
            if (clusters != null) {
                for (var cr : clusters) {
                    sb.append(cr.getCountryCode()).append(",").append(cr.getCountryName()).append(",");
                    sb.append(cr.getClusterLabel()).append(",").append(cr.getRiskLevel()).append(",");
                    sb.append(String.format("%.1f", cr.getRiskScore())).append("\n");
                }
            }
            sb.append("\n=== 地图数据 ===\n国家代码,国家名称,区域,合作指数,事件数\n");
            if (mapData != null) {
                for (var item : mapData) {
                    sb.append(item.get("code")).append(",").append(item.get("name")).append(",");
                    sb.append(item.get("region")).append(",").append(String.format("%.4f", item.get("index"))).append(",");
                    sb.append(item.get("eventCount")).append("\n");
                }
            }
            return sb.toString();
        }

        // ==================== 辅助方法 ====================

        private void appendCard(StringBuilder sb, String icon, String label, String value, String color) {
            sb.append("<div class=\"card\">\n<div style=\"font-size:28px;\">").append(icon).append("</div>\n");
            sb.append("<div class=\"value\" style=\"color:").append(color).append(";\">").append(value).append("</div>\n");
            sb.append("<div class=\"label\">").append(label).append("</div>\n</div>\n");
        }

        private int safeInt(Object v) { return v instanceof Number ? ((Number) v).intValue() : 0; }
        private double safeDouble(Object v) { return v instanceof Number ? ((Number) v).doubleValue() : 0.0; }
        private String safeStr(Object v) { return v != null ? v.toString() : "0"; }

        private String formatNum(int num) {
            if (num >= 10000) return String.format("%.1f万", num / 10000.0);
            return String.format("%,d", num);
        }

    // ==================== 对话框 ====================

    /**
     * 显示帮助信息对话框
     * <p>
     * 列出系统五大核心功能的使用说明：
     * 导航菜单切换、时间轴拖拽、新闻动态展示、地图交互、播放模式。
     * </p>
     */
    private void showHelpDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("帮助");
        alert.setHeaderText("一带一路合作态势分析系统 v1.0");
        alert.setContentText(
            "\uD83D\uDCD6 使用说明：\n\n" +
            "1. 左侧导航菜单切换不同功能模块\n" +
            "2. 底部时间轴可拖拽选择时间范围\n" +
            "3. 右侧新闻面板展示相关新闻动态\n" +
            "4. 在专题地图中点击/悬停国家查看详情\n" +
            "5. 点击 \u25B6 播放按钮可自动推进时间\n\n" +
            "基于 GDELT 2.0 数据源提供分析"
        );
        alert.showAndWait();
    }

    /**
     * 显示系统设置对话框
     * <p>
     * 包含导出存储位置设置：
     * 用户点击"更改位置"选择一个目录后，如果点击"保存"则设为固定导出目录（exportDir），
     * 之后所有导出操作直接存入该目录不再询问；如果点"取消"则不改动。
     * 如果"不使用固定位置"可以清空设置。
     * </p>
     */
    private void showSettingDialog() {
        // 临时变量，保存用户本次选择
        final String[] pendingDir = {exportDir};

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("设置");
        dialog.setHeaderText("系统设置");

        ButtonType saveBtnType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        // 基本信息
        Label infoLabel = new Label("当前配置：\n" +
            "• 数据源: GDELT 真实数据 (SQLite)\n" +
            "• 时间范围: 2018-2026\n" +
            "• 语言: 简体中文\n" +
            "• 主题: 默认");
        infoLabel.setFont(Font.font("Microsoft YaHei", 12));

        // 导出存储位置设置
        Separator sep = new Separator();
        Label exportLabel = new Label("\uD83D\uDCC1 导出存储位置设置:");
        exportLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));

        Label statusLabel = new Label();
        statusLabel.setFont(Font.font("Microsoft YaHei", 11));

        Label exportPathDisplay = new Label(
            pendingDir[0] != null ? pendingDir[0] : "（未设置固定位置，每次导出时自主选择）");
        exportPathDisplay.setStyle("-fx-text-fill: #2c3e50; -fx-font-family: monospace; -fx-font-size: 11px;");
        exportPathDisplay.setWrapText(true);
        exportPathDisplay.setMaxWidth(400);

        Button changeExportDirBtn = new Button("\uD83D\uDCC2 更改位置");
        changeExportDirBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px; -fx-background-radius: 5;");
        changeExportDirBtn.setOnAction(e -> {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("选择导出存储位置");
            if (pendingDir[0] != null) {
                dirChooser.setInitialDirectory(new java.io.File(pendingDir[0]));
            }
            java.io.File dir = dirChooser.showDialog(dialog.getDialogPane().getScene().getWindow());
            if (dir != null) {
                pendingDir[0] = dir.getAbsolutePath();
                exportPathDisplay.setText(dir.getAbsolutePath());
                statusLabel.setText("\u2705 已选择: " + dir.getAbsolutePath() + "（保存后生效）");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px;");
            }
        });

        Button clearExportDirBtn = new Button("\u274C 不使用固定位置");
        clearExportDirBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px; -fx-background-radius: 5; -fx-background-color: #e74c3c; -fx-text-fill: white;");
        clearExportDirBtn.setOnAction(e -> {
            pendingDir[0] = null;
            exportPathDisplay.setText("（未设置固定位置，每次导出时自主选择）");
            statusLabel.setText("\uD83D\uDD04 已取消固定位置，下次导出将弹出选择窗口");
            statusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 11px;");
        });

        HBox btnRow = new HBox(10, changeExportDirBtn, clearExportDirBtn);

        content.getChildren().addAll(infoLabel, sep, exportLabel,
            new HBox(10, exportPathDisplay), btnRow, statusLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        // 点保存才生效
        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                return saveBtnType;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == saveBtnType) {
                exportDir = pendingDir[0];
                if (exportDir != null) {
                    updateStatusBar("\uD83D\uDCC1 导出目录已设置为: " + exportDir);
                } else {
                    updateStatusBar("\uD83D\uDD04 已取消固定导出目录");
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
