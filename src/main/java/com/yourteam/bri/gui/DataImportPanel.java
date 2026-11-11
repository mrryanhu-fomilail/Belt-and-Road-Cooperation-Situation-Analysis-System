package com.yourteam.bri.gui;

import com.bri.config.CountryConfigLoader;
import com.bri.config.DataSource;
import com.bri.download.GdeltDownloadService;
import com.bri.etl.GdeltImporter;
import com.bri.etl.ImportResult;
import com.yourteam.bri.service.DataChangeNotifier;
import com.yourteam.bri.service.BriEngineHolder;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.time.LocalDate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据管理面板 - 文件导入、在线下载、国家配置、数据导出功能
 * <p>
 * 功能覆盖：
 * <ul>
 *   <li>数据导入（F-01）：通过 FileChooser 选择 GDELT 数据文件，写入 SQLite 数据库 + 同步到算法引擎</li>
 *   <li>在线下载：从 GDELT 2.0 官网自动扫描日期范围 → 下载 ZIP → 解压 → 导入数据库 → 构建索引</li>
 *   <li>一带一路国家配置（F-03~F-05）：内置 JSON 配置 150+ 国家，支持按子区域筛选查看、导出配置</li>
 *   <li>数据导出（F-05）：支持导出为 CSV / Excel 格式</li>
 * </ul>
 * </p>
 */
public class DataImportPanel extends VBox {

    private static final String DB_PATH = "bri.db";

    private Label filePathLabel;
    private Label importStatusLabel;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea configArea;
    private ComboBox<String> filterCombo;
    private Button startImportBtn;
    private Button stopBtn;
    private ComboBox<String> regionFilter;

    private File selectedFile = null;
    private final AtomicBoolean isImporting = new AtomicBoolean(false);

    // ---- 在线下载相关字段 ----
    private DatePicker downloadStartDatePicker;
    private DatePicker downloadEndDatePicker;
    private Label downloadStatusLabel;
    private ProgressBar downloadProgressBar;
    private Label downloadProgressLabel;
    private Button scanBtn;
    private Button startDownloadBtn;
    private Button cancelDownloadBtn;
    private ComboBox<String> downloadFilterCombo;
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private GdeltDownloadService downloadService;
    private int currentScanTotal = 0;
    private int currentScanMissing = 0;

    /** 从 JSON 加载的完整国家列表 */
    private List<Map<String, Object>> allCountries = new ArrayList<>();

    /** 用户设置的固定导出目录（由 MainWindow 传入），null 表示每次自主选择 */
    private String exportDir = null;

    /** 回调：获取最新的 exportDir（来自 MainWindow） */
    private java.util.function.Supplier<String> exportDirSupplier = null;

    /**
     * 设置导出目录（由 MainWindow 在创建时传入）
     */
    public void setExportDir(String dir) {
        this.exportDir = dir;
    }

    /**
     * 设置导出目录提供者（MainWindow 传入 lambda，确保每次获取最新值）
     */
    public void setExportDirSupplier(java.util.function.Supplier<String> supplier) {
        this.exportDirSupplier = supplier;
    }

    /**
     * 获取当前有效的导出目录
     */
    private String getEffectiveExportDir() {
        if (exportDirSupplier != null) {
            return exportDirSupplier.get();
        }
        return exportDir;
    }

    public DataImportPanel() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: #f5f7fa;");
        setPrefWidth(800);

        // 启动时加载内置 JSON 国家配置
        loadCountryConfig();

        Label titleLabel = new Label("\uD83D\uDCC1 数据管理");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        VBox importBox = createImportBox();
        VBox downloadBox = createDownloadBox();
        VBox configBox = createConfigBox();
        HBox exportBox = createExportBox();

        VBox contentWrapper = new VBox(15, titleLabel, importBox, downloadBox, configBox, exportBox);
        contentWrapper.setPadding(new Insets(0, 0, 20, 0));

        ScrollPane scrollPane = new ScrollPane(contentWrapper);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: #f5f7fa; -fx-background-color: #f5f7fa; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
    }

    /**
     * 从内置 JSON 文件加载一带一路国家配置
     */
    @SuppressWarnings("unchecked")
    private void loadCountryConfig() {
        allCountries.clear();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("bri_countries.json")) {
            if (is == null) {
                Path jsonPath = Path.of("src/main/resources/bri_countries.json");
                if (Files.exists(jsonPath)) {
                    String raw = Files.readString(jsonPath, StandardCharsets.UTF_8);
                    raw = raw.replaceAll("//.*?\\r?\\n", "\n").replaceAll("/\\*.*?\\*/", "");
                    allCountries = parseJsonArray(raw);
                } else {
                    throw new IOException("JSON 配置文件不存在");
                }
            } else {
                String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                raw = raw.replaceAll("//.*?\\r?\\n", "\n").replaceAll("/\\*.*?\\*/", "");
                allCountries = parseJsonArray(raw);
            }
        } catch (Exception e) {
            System.err.println("[DataImportPanel] 加载国家配置失败: " + e.getMessage());
        }
    }

    /**
     * 手动解析 JSON 数组（不依赖任何第三方 JSON 库）
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        int idx = json.indexOf('[');
        if (idx < 0) return result;
        int end = json.lastIndexOf(']');
        if (end < 0) return result;
        String content = json.substring(idx + 1, end);

        // 逐个大括号提取对象
        int i = 0;
        while (i < content.length()) {
            int objStart = content.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = content.indexOf('}', objStart);
            if (objEnd < 0) break;
            String objStr = content.substring(objStart + 1, objEnd);
            Map<String, Object> map = new LinkedHashMap<>();
            String[] pairs = objStr.split(",");
            for (String pair : pairs) {
                int colonIdx = pair.indexOf(':');
                if (colonIdx < 0) continue;
                String key = pair.substring(0, colonIdx).trim().replaceAll("^\"|\"$", "");
                String val = pair.substring(colonIdx + 1).trim().replaceAll("^\"|\"$", "");
                if (key.equals("is_bri")) {
                    map.put(key, Integer.parseInt(val));
                } else {
                    map.put(key, val);
                }
            }
            if (!map.isEmpty()) result.add(map);
            i = objEnd + 1;
        }
        return result;
    }

    /**
     * 子区域中文名 → 代码映射
     */
    private String regionNameToCode(String cn) {
        Map<String, String> map = new HashMap<>();
        map.put("亚洲", "ASIA");
        map.put("欧洲", "EU");
        map.put("非洲", "AF");
        map.put("北美洲", "NAM");
        map.put("南美洲", "SAM");
        map.put("大洋洲", "OCE");
        return map.getOrDefault(cn, "");
    }

    /**
     * 子区域代码 → 中文名映射
     */
    private String regionCodeToName(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("EA", "东亚");
        map.put("SEA", "东南亚");
        map.put("CA", "中亚");
        map.put("SA", "南亚");
        map.put("ME", "中东");
        map.put("EU", "欧洲");
        map.put("AF", "非洲");
        map.put("NAM", "北美洲");
        map.put("SAM", "南美洲");
        map.put("OCE", "大洋洲");
        return map.getOrDefault(code, code);
    }

    /**
     * 判断子区域代码是否属于指定大洲
     */
    private boolean isRegionInContinent(String subregion, String continentCode) {
        if (continentCode == null || continentCode.equals("全部区域")) return true;
        Map<String, List<String>> mapping = new HashMap<>();
        mapping.put("ASIA", Arrays.asList("EA", "SEA", "CA", "SA", "ME"));
        mapping.put("EU", Arrays.asList("EU"));
        mapping.put("AF", Arrays.asList("AF"));
        mapping.put("NAM", Arrays.asList("NAM"));
        mapping.put("SAM", Arrays.asList("SAM"));
        mapping.put("OCE", Arrays.asList("OCE"));
        List<String> codes = mapping.get(continentCode);
        return codes != null && codes.contains(subregion);
    }

    private VBox createImportBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83D\uDCE5 数据导入");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        Button selectFileBtn = new Button("\uD83D\uDCC2 选择文件");
        selectFileBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 8px 16px; -fx-background-radius: 5;");
        selectFileBtn.setOnAction(e -> selectFile());

        filePathLabel = new Label("未选择文件");
        filePathLabel.setFont(Font.font("Microsoft YaHei", 12));
        filePathLabel.setTextFill(Color.GRAY);

        fileRow.getChildren().addAll(selectFileBtn, filePathLabel);

        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        Label filterLabel = new Label("国家过滤:");
        filterLabel.setFont(Font.font("Microsoft YaHei", 12));

        filterCombo = new ComboBox<>();
        filterCombo.setPrefWidth(220);
        filterCombo.getItems().addAll("双边均为一带一路国家", "至少一方为一带一路国家", "全部国家（不过滤）");
        filterCombo.getSelectionModel().select(0);

        startImportBtn = new Button("\u25B6 开始导入");
        startImportBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8px 16px; -fx-background-radius: 5;");
        startImportBtn.setOnAction(e -> startRealImport());

        stopBtn = new Button("\u23F9 停止");
        stopBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 16px; -fx-background-radius: 5;");
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> stopImport());

        filterRow.getChildren().addAll(filterLabel, filterCombo, startImportBtn, stopBtn);

        HBox progressRow = new HBox(10);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setPrefHeight(20);

        progressLabel = new Label("0%");
        progressLabel.setFont(Font.font("Microsoft YaHei", 12));

        progressRow.getChildren().addAll(new Label("进度:"), progressBar, progressLabel);

        importStatusLabel = new Label("就绪");
        importStatusLabel.setFont(Font.font("Microsoft YaHei", 12));
        importStatusLabel.setTextFill(Color.GRAY);

        box.getChildren().addAll(sectionTitle, fileRow, filterRow, progressRow, importStatusLabel);
        return box;
    }

    // ==================== 在线下载区域 ====================

    private VBox createDownloadBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83C\uDF10 在线下载 GDELT 数据");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        // 日期选择行
        HBox dateRow = new HBox(12);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        Label startLabel = new Label("开始日期:");
        startLabel.setFont(Font.font("Microsoft YaHei", 12));
        downloadStartDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        downloadStartDatePicker.setPrefWidth(140);

        Label endLabel = new Label("结束日期:");
        endLabel.setFont(Font.font("Microsoft YaHei", 12));
        downloadEndDatePicker = new DatePicker(LocalDate.now());
        downloadEndDatePicker.setPrefWidth(140);

        scanBtn = new Button("\uD83D\uDD0D 扫描可用文件");
        scanBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 8px 14px; -fx-background-radius: 5;");
        scanBtn.setOnAction(e -> startScan());

        dateRow.getChildren().addAll(startLabel, downloadStartDatePicker, endLabel, downloadEndDatePicker, scanBtn);

        // BRI 国家过滤行
        HBox downloadFilterRow = new HBox(12);
        downloadFilterRow.setAlignment(Pos.CENTER_LEFT);

        Label downloadFilterLabel = new Label("国家过滤:");
        downloadFilterLabel.setFont(Font.font("Microsoft YaHei", 12));

        downloadFilterCombo = new ComboBox<>();
        downloadFilterCombo.setPrefWidth(220);
        downloadFilterCombo.getItems().addAll("双边均为一带一路国家", "至少一方为一带一路国家", "全部国家（不过滤）");
        downloadFilterCombo.getSelectionModel().select(1); // 默认：至少一方

        downloadFilterRow.getChildren().addAll(downloadFilterLabel, downloadFilterCombo);

        // 按钮行
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        startDownloadBtn = new Button("\u2B07 开始下载");
        startDownloadBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8px 18px; -fx-background-radius: 5;");
        startDownloadBtn.setDisable(true);
        startDownloadBtn.setOnAction(e -> startOnlineDownload());

        cancelDownloadBtn = new Button("\u23F9 取消");
        cancelDownloadBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px; -fx-background-radius: 5;");
        cancelDownloadBtn.setDisable(true);
        cancelDownloadBtn.setOnAction(e -> cancelDownload());

        btnRow.getChildren().addAll(startDownloadBtn, cancelDownloadBtn);

        // 进度行
        HBox progressRow = new HBox(10);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        downloadProgressBar = new ProgressBar(0);
        downloadProgressBar.setPrefWidth(400);
        downloadProgressBar.setPrefHeight(20);

        downloadProgressLabel = new Label("0%");
        downloadProgressLabel.setFont(Font.font("Microsoft YaHei", 12));

        progressRow.getChildren().addAll(new Label("进度:"), downloadProgressBar, downloadProgressLabel);

        // 状态标签
        downloadStatusLabel = new Label("选择日期后点击扫描");
        downloadStatusLabel.setFont(Font.font("Microsoft YaHei", 12));
        downloadStatusLabel.setTextFill(Color.GRAY);
        downloadStatusLabel.setWrapText(true);

        box.getChildren().addAll(sectionTitle, dateRow, downloadFilterRow, btnRow, progressRow, downloadStatusLabel);
        return box;
    }

    /** 初始化 DownloadService（懒加载） */
    private GdeltDownloadService getDownloadService() {
        if (downloadService == null) {
            downloadService = new GdeltDownloadService(DataSource.getInstance(DB_PATH));
        }
        return downloadService;
    }

    /** 扫描可用文件 */
    private void startScan() {
        LocalDate startDate = downloadStartDatePicker.getValue();
        LocalDate endDate = downloadEndDatePicker.getValue();

        if (startDate == null || endDate == null) {
            showAlert("提示", "请选择开始日期和结束日期");
            return;
        }
        if (endDate.isBefore(startDate)) {
            showAlert("提示", "结束日期不能早于开始日期");
            return;
        }

        scanBtn.setDisable(true);
        downloadStatusLabel.setText("\u23F3 正在扫描 GDELT 文件列表...");
        downloadStatusLabel.setTextFill(Color.web("#3498db"));

        new Thread(() -> {
            try {
                GdeltDownloadService service = getDownloadService();
                GdeltDownloadService.ScanResult result = service.scan(startDate, endDate);

                currentScanTotal = result.totalFiles;
                currentScanMissing = result.missingFiles;

                Platform.runLater(() -> {
                    if (result.totalFiles == 0) {
                        downloadStatusLabel.setText(
                            "\u26A0 该日期范围内未找到 GDELT 文件，请检查日期或网络连接");
                        downloadStatusLabel.setTextFill(Color.web("#e74c3c"));
                    } else if (result.missingFiles == 0) {
                        downloadStatusLabel.setText(String.format(
                            "\u2705 共 %d 个文件全部已缓存！无需重复下载。\n已缓存数据范围: %s ~ %s",
                            result.totalFiles,
                            service.getTracker().getDataDateRange() != null
                                ? service.getTracker().getDataDateRange()[0].toString() : "N/A",
                            service.getTracker().getDataDateRange() != null
                                ? service.getTracker().getDataDateRange()[1].toString() : "N/A"));
                        downloadStatusLabel.setTextFill(Color.web("#2ecc71"));
                    } else {
                        downloadStatusLabel.setText(String.format(
                            "\uD83D\uDCCA 扫描结果：共 %d 个文件 | 已缓存 %d 个 | \u2728 需下载 %d 个新文件",
                            result.totalFiles, result.cachedFiles, result.missingFiles));
                        downloadStatusLabel.setTextFill(Color.web("#27ae60"));
                        startDownloadBtn.setDisable(false);
                    }
                    scanBtn.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    downloadStatusLabel.setText("\u274C 扫描失败: " + e.getMessage());
                    downloadStatusLabel.setTextFill(Color.web("#e74c3c"));
                    scanBtn.setDisable(false);
                });
            }
        }).start();
    }

    /** 开始在线下载 */
    private void startOnlineDownload() {
        LocalDate startDate = downloadStartDatePicker.getValue();
        LocalDate endDate = downloadEndDatePicker.getValue();

        if (startDate == null || endDate == null) {
            showAlert("提示", "请选择日期范围");
            return;
        }
        if (currentScanMissing <= 0) {
            showAlert("提示", "没有需要下载的文件，请先扫描");
            return;
        }

        if (!isDownloading.compareAndSet(false, true)) return;

        // 初始化数据库
        DataSource ds = DataSource.getInstance(DB_PATH);
        ds.initIfAbsent();
        try { new CountryConfigLoader().loadToDatabase(ds); } catch (Exception ignored) {}

        startDownloadBtn.setDisable(true);
        scanBtn.setDisable(true);
        cancelDownloadBtn.setDisable(false);
        downloadStatusLabel.setText("\u23F3 正在下载中...");
        downloadStatusLabel.setTextFill(Color.web("#3498db"));
        downloadProgressBar.setProgress(0);
        downloadProgressLabel.setText("0%");

        // 读取下载模块专属的 BRI 过滤模式
        String selectedFilter = downloadFilterCombo.getValue();
        GdeltImporter.BriFilterMode fm;
        if ("双边均为一带一路国家".equals(selectedFilter)) {
            fm = GdeltImporter.BriFilterMode.BOTH;
        } else if ("全部国家（不过滤）".equals(selectedFilter)) {
            fm = GdeltImporter.BriFilterMode.NONE;
        } else {
            fm = GdeltImporter.BriFilterMode.ANY;
        }
        getDownloadService().setBriFilterMode(fm);

        new Thread(() -> {
            try {
                getDownloadService().downloadRange(startDate, endDate, info -> {
                    Platform.runLater(() -> {
                        downloadProgressBar.setProgress(info.percent);
                        downloadProgressLabel.setText(String.format("%.0f%%", info.percent * 100));

                        String phaseText;
                        switch (info.phase) {
                            case FETCHING_MASTER:
                                phaseText = "\uD83D\uDCCB " + info.statusText;
                                break;
                            case DOWNLOADING:
                                phaseText = String.format("\u2B07 下载中 (%d/%d): %s",
                                    info.currentFileIndex, info.totalFiles, info.statusText);
                                break;
                            case IMPORTING:
                                phaseText = "\uD83D\uDCE5 " + info.statusText;
                                break;
                            case BUILDING_INDEX:
                                phaseText = "\uD83D\uDD27 " + info.statusText;
                                break;
                            case COMPLETE:
                                phaseText = "\u2705 " + info.statusText;
                                break;
                            default:
                                phaseText = info.statusText;
                        }
                        downloadStatusLabel.setText(phaseText);

                        if (info.phase == GdeltDownloadService.Phase.COMPLETE) {
                            downloadStatusLabel.setTextFill(Color.web("#2ecc71"));
                            onDownloadComplete();
                        } else {
                            downloadStatusLabel.setTextFill(Color.web("#3498db"));
                        }
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    downloadStatusLabel.setText("\u274C 下载失败: " + e.getMessage());
                    downloadStatusLabel.setTextFill(Color.web("#e74c3c"));
                    resetDownloadState();
                });
            }
        }).start();
    }

    /** 下载完成后处理：加载到引擎 + 刷新面板 */
    private void onDownloadComplete() {
        new Thread(() -> {
            try {
                // 从数据库加载所有事件到引擎
                DataSource ds = DataSource.getInstance(DB_PATH);
                java.util.List<com.bri.model.Event> events =
                    com.bri.dao.DaoFactory.getInstance(DB_PATH).eventDao()
                        .query(new com.bri.dao.EventQuery.Builder().build());

                BriEngineHolder.resetEngine();
                BriEngineHolder.getEngine().loadFromEvents(events);

                Platform.runLater(() -> {
                    downloadStatusLabel.setText(
                        downloadStatusLabel.getText() + "\n\uD83D\uDD27 分析引擎索引已更新，所有面板已刷新");
                    resetDownloadState();
                    DataChangeNotifier.notifyDataChanged();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    downloadStatusLabel.setText(
                        downloadStatusLabel.getText() + "\n\u26A0 引擎加载失败: " + e.getMessage());
                    resetDownloadState();
                });
            }
        }).start();
    }

    /** 取消下载 */
    private void cancelDownload() {
        if (downloadService != null) {
            downloadService.cancel();
        }
        isDownloading.set(false);
        downloadStatusLabel.setText("\u23F9 下载已取消");
        downloadStatusLabel.setTextFill(Color.web("#e74c3c"));
        resetDownloadState();
    }

    /** 重置下载按钮状态 */
    private void resetDownloadState() {
        isDownloading.set(false);
        scanBtn.setDisable(false);
        cancelDownloadBtn.setDisable(true);
        startDownloadBtn.setDisable(currentScanMissing <= 0);
    }

    private VBox createConfigBox() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83C\uDF0D 一带一路国家配置");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        HBox configRow = new HBox(10);
        configRow.setAlignment(Pos.CENTER_LEFT);

        regionFilter = new ComboBox<>();
        regionFilter.setPrefWidth(150);
        regionFilter.getItems().addAll("全部区域", "亚洲", "欧洲", "非洲", "北美洲", "南美洲", "大洋洲");
        regionFilter.getSelectionModel().select(0);

        Button viewBtn = new Button("\uD83D\uDCCB 查看国家列表");
        viewBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px; -fx-background-radius: 5;");

        Button exportConfigBtn = new Button("\uD83D\uDCE4 导出配置");
        exportConfigBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px; -fx-background-radius: 5;");

        configRow.getChildren().addAll(regionFilter, viewBtn, exportConfigBtn);

        configArea = new TextArea();
        configArea.setEditable(false);
        configArea.setPrefHeight(120);
        configArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        // 初始显示统计信息
        updateConfigDisplay(null);

        viewBtn.setOnAction(e -> {
            String region = regionFilter.getValue();
            updateConfigDisplay(region);
        });

        exportConfigBtn.setOnAction(e -> exportConfig());

        box.getChildren().addAll(sectionTitle, configRow, configArea);
        return box;
    }

    /**
     * 更新国家配置显示区域
     */
    private void updateConfigDisplay(String region) {
        StringBuilder sb = new StringBuilder();

        if (region == null || region.equals("全部区域")) {
            // 统计各区域数量
            Map<String, Integer> regionCount = new LinkedHashMap<>();
            for (Map<String, Object> c : allCountries) {
                String sr = (String) c.get("subregion");
                if (sr == null || sr.isEmpty()) sr = "未知";
                regionCount.merge(sr, 1, Integer::sum);
            }

            sb.append("\uD83C\uDF10 一带一路沿线国家配置 (").append(allCountries.size()).append(" 个国家)\n\n");
            for (Map.Entry<String, Integer> e : regionCount.entrySet()) {
                sb.append("  ").append(regionCodeToName(e.getKey())).append(": ").append(e.getValue()).append(" 个国家\n");
            }
            sb.append("\n--- 全部国家列表 ---\n\n");
            for (Map<String, Object> c : allCountries) {
                String code = (String) c.get("cameo_code");
                String cn = (String) c.get("name_cn");
                String en = (String) c.get("name_en");
                String sr = regionCodeToName((String) c.get("subregion"));
                sb.append("  ").append(cn).append(" (").append(code).append(") - ").append(sr).append("\n");
            }
        } else {
            String continentCode = regionNameToCode(region);
            sb.append(region).append(" 国家列表:\n\n");
            int count = 0;
            for (Map<String, Object> c : allCountries) {
                String subregion = (String) c.get("subregion");
                if (subregion != null && isRegionInContinent(subregion, continentCode)) {
                    String code2 = (String) c.get("cameo_code");
                    String cn = (String) c.get("name_cn");
                    String en = (String) c.get("name_en");
                    sb.append("  ").append(cn).append(" (").append(code2).append(") - ").append(regionCodeToName(subregion)).append("\n");
                    count++;
                }
            }
            if (count == 0) {
                sb.append("  (暂无数据)\n");
            }
        }

        configArea.setText(sb.toString());
    }

    /**
     * 导出配置为 JSON 文件
     * <p>
     * 如果设置了固定导出目录（exportDir != null），直接导出到该目录；
     * 否则弹出 FileChooser 让用户自主选择。
     * </p>
     */
    private void exportConfig() {
        String dir = getEffectiveExportDir();
        String json = buildConfigJson();

        if (dir != null) {
            // 固定目录：直接导出，文件名自动加时间戳避免覆盖
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            java.io.File file = new java.io.File(dir, "bri_countries_export_" + ts + ".json");
            try {
                java.nio.file.Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
                showAlert("导出成功", "国家配置已导出至: " + file.getAbsolutePath());
            } catch (IOException ex) {
                showAlert("导出失败", "导出配置时出错: " + ex.getMessage());
            }
        } else {
            // 未设置固定目录，弹出文件选择器
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出国家配置");
            fileChooser.setInitialFileName("bri_countries_export.json");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON 配置文件 (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
            );
            java.io.File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    java.nio.file.Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
                    showAlert("导出成功", "国家配置已导出至: " + file.getAbsolutePath());
                } catch (IOException ex) {
                    showAlert("导出失败", "导出配置时出错: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * 构建配置 JSON 字符串
     */
    private String buildConfigJson() {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < allCountries.size(); i++) {
            Map<String, Object> c = allCountries.get(i);
            json.append("  {")
                .append("\"cameo_code\":\"").append(c.get("cameo_code")).append("\",")
                .append("\"name_cn\":\"").append(c.get("name_cn")).append("\",")
                .append("\"name_en\":\"").append(c.get("name_en")).append("\",")
                .append("\"subregion\":\"").append(c.get("subregion")).append("\",")
                .append("\"is_bri\":").append(c.get("is_bri"))
                .append("}");
            if (i < allCountries.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("]");
        return json.toString();
    }

    private HBox createExportBox() {
        HBox box = new HBox(15);
        box.setPadding(new Insets(15));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83D\uDCE5 数据导出");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        Button exportCSVBtn = new Button("\uD83D\uDCC4 导出 CSV");
        exportCSVBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-padding: 8px 16px; -fx-background-radius: 5;");

        Button exportExcelBtn = new Button("\uD83D\uDCC8 导出 Excel");
        exportExcelBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8px 16px; -fx-background-radius: 5;");

        // 显示当前导出位置状态
        Label exportPathHint = new Label();
        exportPathHint.setFont(Font.font("Microsoft YaHei", 10));
        updateExportHint(exportPathHint);

        Label exportInfo = new Label("导出分析结果为标准格式文件");
        exportInfo.setFont(Font.font("Microsoft YaHei", 11));
        exportInfo.setTextFill(Color.GRAY);

        box.getChildren().addAll(sectionTitle, exportCSVBtn, exportExcelBtn, exportInfo, exportPathHint);

        exportCSVBtn.setOnAction(e -> exportData("csv"));
        exportExcelBtn.setOnAction(e -> exportData("excel"));

        return box;
    }

    /**
     * 更新导出路径提示
     */
    private void updateExportHint(Label hint) {
        if (hint != null) {
            String dir = getEffectiveExportDir();
            if (dir != null) {
                hint.setText("\uD83D\uDCC1 导出至: " + dir);
                hint.setTextFill(Color.web("#27ae60"));
            } else {
                hint.setText("\uD83D\uDCC1 每次导出时自主选择位置");
                hint.setTextFill(Color.web("#e67e22"));
            }
        }
    }

    /**
     * 导出数据（CSV / Excel）
     * <p>
     * 如果设置了固定导出目录（exportDir != null），直接导出到该目录；
     * 否则弹出 FileChooser 让用户选择。
     * </p>
     */
    private void exportData(String format) {
        if ("csv".equals(format)) {
            // CSV 导出：生成分析报告
            String content = buildCsvContent();
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String dir = getEffectiveExportDir();

            if (dir != null) {
                String fileName = "bri_analysis_report_" + ts + ".csv";
                java.io.File file = new java.io.File(dir, fileName);
                try {
                    java.nio.file.Files.write(file.toPath(), contentBytes);
                    showAlert("导出成功", "CSV 数据已导出至:\n" + file.getAbsolutePath());
                } catch (IOException ex) {
                    showAlert("导出失败", "导出时出错: " + ex.getMessage());
                }
            } else {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("导出 CSV 数据");
                fileChooser.setInitialFileName("bri_analysis_report_" + ts + ".csv");
                fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CSV 文件 (*.csv)", "*.csv"),
                    new FileChooser.ExtensionFilter("所有文件", "*.*")
                );
                java.io.File file = fileChooser.showSaveDialog(null);
                if (file != null) {
                    try {
                        java.nio.file.Files.write(file.toPath(), contentBytes);
                        showAlert("导出成功", "CSV 数据已导出至:\n" + file.getAbsolutePath());
                    } catch (IOException ex) {
                        showAlert("导出失败", "导出时出错: " + ex.getMessage());
                    }
                }
            }
        } else {
            // XLSX 导出：使用 POI 生成真正的 Excel 文件
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String dir = getEffectiveExportDir();

            try {
                // 从引擎获取数据
                com.bri.engine.BriAnalysisEngine engine = com.yourteam.bri.service.BriEngineHolder.getEngine();
                int year = java.time.LocalDate.now().getYear();
                int month = java.time.LocalDate.now().getMonthValue();

                // 创建 Workbook
                org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();

                // ---- Sheet 1: 总体统计 ----
                org.apache.poi.ss.usermodel.Sheet summarySheet = wb.createSheet("总体统计");
                com.bri.model.DashboardStats stats = engine.getDashboardStats();
                String[][] summaryData = {
                    {"指标", "数值"},
                    {"国家总数", String.valueOf(stats.getTotalCountries())},
                    {"合作事件数", String.valueOf(stats.getTotalCooperationEvents())},
                    {"冲突事件数", String.valueOf(stats.getTotalConflictEvents())},
                    {"平均合作指数", String.format("%.2f", stats.getAvgCooperationIndex())},
                    {"平均冲突比率", String.format("%.2f", stats.getAvgConflictRatio())},
                    {"平均语调(avgTone)", String.format("%.2f", stats.getAvgTone())}
                };
                for (int i = 0; i < summaryData.length; i++) {
                    org.apache.poi.ss.usermodel.Row row = summarySheet.createRow(i);
                    row.createCell(0).setCellValue(summaryData[i][0]);
                    row.createCell(1).setCellValue(summaryData[i][1]);
                }
                summarySheet.autoSizeColumn(0);
                summarySheet.autoSizeColumn(1);

                // ---- Sheet 2: 合作指数排名 ----
                org.apache.poi.ss.usermodel.Sheet rankSheet = wb.createSheet("合作指数排名");
                String[] rankHeaders = {"排名", "国家代码", "国家名称", "合作指数", "区域"};
                org.apache.poi.ss.usermodel.Row rankHeaderRow = rankSheet.createRow(0);
                for (int i = 0; i < rankHeaders.length; i++) {
                    rankHeaderRow.createCell(i).setCellValue(rankHeaders[i]);
                }
                List<Map.Entry<String, Double>> ranks = engine.getCoopRanking(year, month);
                int rankRow = 1;
                for (Map.Entry<String, Double> entry : ranks) {
                    org.apache.poi.ss.usermodel.Row row = rankSheet.createRow(rankRow++);
                    com.bri.model.Country c = engine.getCountry(entry.getKey());
                    String name = c != null ? c.getNameCn() : entry.getKey();
                    String region = "";
                    if (c != null) {
                        com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
                        region = sr != null ? sr.getNameCn() : "";
                    }
                    row.createCell(0).setCellValue(rankRow - 1);
                    row.createCell(1).setCellValue(entry.getKey());
                    row.createCell(2).setCellValue(name);
                    row.createCell(3).setCellValue(entry.getValue());
                    row.createCell(4).setCellValue(region);
                }
                for (int i = 0; i < rankHeaders.length; i++) rankSheet.autoSizeColumn(i);

                // ---- Sheet 3: 热点与风险 ----
                org.apache.poi.ss.usermodel.Sheet hotspotSheet = wb.createSheet("热点与风险");
                // 热点
                hotspotSheet.createRow(0).createCell(0).setCellValue("=== 合作热点国家 TOP5 ===");
                String[] hotspotHeaders = {"国家代码", "国家名称", "增长率"};
                org.apache.poi.ss.usermodel.Row hhRow = hotspotSheet.createRow(1);
                for (int i = 0; i < hotspotHeaders.length; i++) hhRow.createCell(i).setCellValue(hotspotHeaders[i]);
                int hRow = 2;
                for (com.bri.model.HotspotCountry h : engine.getHotspots(5)) {
                    com.bri.model.Country c = engine.getCountry(h.getCountryCode());
                    String name = c != null ? c.getNameCn() : h.getCountryCode();
                    org.apache.poi.ss.usermodel.Row row = hotspotSheet.createRow(hRow++);
                    row.createCell(0).setCellValue(h.getCountryCode());
                    row.createCell(1).setCellValue(name);
                    row.createCell(2).setCellValue(String.format("%.2f%%", h.getGrowthRate()));
                }
                // 风险
                int riskStartRow = hRow + 1;
                hotspotSheet.createRow(riskStartRow).createCell(0).setCellValue("=== 风险预警 TOP5 ===");
                String[] riskHeaders = {"国家代码", "国家名称", "风险等级"};
                org.apache.poi.ss.usermodel.Row rhRow = hotspotSheet.createRow(riskStartRow + 1);
                for (int i = 0; i < riskHeaders.length; i++) rhRow.createCell(i).setCellValue(riskHeaders[i]);
                int rRow = riskStartRow + 2;
                for (com.bri.model.RiskRanking rk : engine.getRiskRankings(5)) {
                    com.bri.model.Country c = engine.getCountry(rk.getCountryCode());
                    String name = c != null ? c.getNameCn() : rk.getCountryCode();
                    org.apache.poi.ss.usermodel.Row row = hotspotSheet.createRow(rRow++);
                    row.createCell(0).setCellValue(rk.getCountryCode());
                    row.createCell(1).setCellValue(name);
                    row.createCell(2).setCellValue(rk.getRiskLevel());
                }
                for (int i = 0; i < 3; i++) hotspotSheet.autoSizeColumn(i);

                // ---- Sheet 4: 聚类结果 ----
                org.apache.poi.ss.usermodel.Sheet clusterSheet = wb.createSheet("聚类结果");
                String[] clusterHeaders = {"国家代码", "国家名称", "聚类ID", "聚类标签", "风险等级", "合作指数", "冲突比率"};
                org.apache.poi.ss.usermodel.Row chRow = clusterSheet.createRow(0);
                for (int i = 0; i < clusterHeaders.length; i++) chRow.createCell(i).setCellValue(clusterHeaders[i]);
                int cRow = 1;
                for (com.bri.model.ClusterResult cl : engine.getClusters()) {
                    com.bri.model.Country c = engine.getCountry(cl.getCountryCode());
                    String name = c != null ? c.getNameCn() : cl.getCountryCode();
                    org.apache.poi.ss.usermodel.Row row = clusterSheet.createRow(cRow++);
                    row.createCell(0).setCellValue(cl.getCountryCode());
                    row.createCell(1).setCellValue(name);
                    row.createCell(2).setCellValue(cl.getClusterId());
                    row.createCell(3).setCellValue(cl.getClusterLabel());
                    row.createCell(4).setCellValue(cl.getRiskLevel());
                    row.createCell(5).setCellValue(cl.getCooperationIndex());
                    row.createCell(6).setCellValue(cl.getConflictRatio());
                }
                for (int i = 0; i < clusterHeaders.length; i++) clusterSheet.autoSizeColumn(i);

                // ---- Sheet 5: 月度合作指数 ----
                org.apache.poi.ss.usermodel.Sheet monthlySheet = wb.createSheet("月度合作指数");
                org.apache.poi.ss.usermodel.Row mhRow = monthlySheet.createRow(0);
                mhRow.createCell(0).setCellValue("国家代码");
                mhRow.createCell(1).setCellValue("国家名称");
                mhRow.createCell(2).setCellValue("区域");
                for (int m = 1; m <= 12; m++) {
                    mhRow.createCell(m + 2).setCellValue(year + "-" + String.format("%02d", m));
                }
                int mRow = 1;
                for (com.bri.model.Country c : engine.getCountries()) {
                    org.apache.poi.ss.usermodel.Row row = monthlySheet.createRow(mRow++);
                    row.createCell(0).setCellValue(c.getCameoCode());
                    row.createCell(1).setCellValue(c.getNameCn());
                    com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
                    row.createCell(2).setCellValue(sr != null ? sr.getNameCn() : "");
                    for (int m = 1; m <= 12; m++) {
                        row.createCell(m + 2).setCellValue(engine.getCoopIndex(c.getCameoCode(), year, m));
                    }
                }
                for (int i = 0; i < 15; i++) monthlySheet.autoSizeColumn(i);

                // 写入文件
                if (dir != null) {
                    String fileName = "bri_analysis_report_" + ts + ".xlsx";
                    java.io.File file = new java.io.File(dir, fileName);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        wb.write(fos);
                    }
                    wb.close();
                    showAlert("导出成功", "Excel 报告已导出至:\n" + file.getAbsolutePath());
                } else {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("导出 Excel 报告");
                    fileChooser.setInitialFileName("bri_analysis_report_" + ts + ".xlsx");
                    fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Excel 文件 (*.xlsx)", "*.xlsx"),
                        new FileChooser.ExtensionFilter("所有文件", "*.*")
                    );
                    java.io.File file = fileChooser.showSaveDialog(null);
                    if (file != null) {
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            wb.write(fos);
                        }
                        wb.close();
                        showAlert("导出成功", "Excel 报告已导出至:\n" + file.getAbsolutePath());
                    } else {
                        wb.close();
                    }
                }

            } catch (Exception ex) {
                showAlert("导出失败", "生成 Excel 时出错: " + ex.getMessage());
            }
        }
    }

    /**
     * 构建 CSV 导出内容——从引擎获取真实分析结果
     */
    private String buildCsvContent() {
        StringBuilder sb = new StringBuilder();
        try {
            com.bri.engine.BriAnalysisEngine engine = com.yourteam.bri.service.BriEngineHolder.getEngine();
            int year = java.time.LocalDate.now().getYear();
            int month = java.time.LocalDate.now().getMonthValue();

            // 1) 仪表盘统计概要
            com.bri.model.DashboardStats stats = engine.getDashboardStats();
            sb.append("=== 一带一路合作态势分析报告 ===\n");
            sb.append("生成时间: ").append(new Date()).append("\n\n");
            sb.append("=== 1. 总体统计 ===\n");
            sb.append("国家总数,").append(stats.getTotalCountries()).append("\n");
            sb.append("合作事件数,").append(stats.getTotalCooperationEvents()).append("\n");
            sb.append("冲突事件数,").append(stats.getTotalConflictEvents()).append("\n");
            sb.append("平均合作指数,").append(String.format("%.2f", stats.getAvgCooperationIndex())).append("\n");
            sb.append("平均冲突比率,").append(String.format("%.2f", stats.getAvgConflictRatio())).append("\n");
            sb.append("平均语调(avgTone),").append(String.format("%.2f", stats.getAvgTone())).append("\n\n");

            // 2) 合作指数排名
            sb.append("=== 2. 合作指数排名 (").append(year).append("-").append(String.format("%02d", month)).append(") ===\n");
            sb.append("排名,国家代码,国家名称,合作指数,区域\n");
            List<Map.Entry<String, Double>> ranks = engine.getCoopRanking(year, month);
            int rank = 1;
            for (Map.Entry<String, Double> entry : ranks) {
                String code = entry.getKey();
                com.bri.model.Country c = engine.getCountry(code);
                String name = c != null ? c.getNameCn() : code;
                String region = "";
                if (c != null) {
                    com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
                    region = sr != null ? sr.getNameCn() : "";
                }
                sb.append(rank).append(",").append(code).append(",").append(name).append(",")
                  .append(String.format("%.4f", entry.getValue())).append(",").append(region).append("\n");
                rank++;
            }

            // 3) 热点国家
            sb.append("\n=== 3. 合作热点国家 TOP5 ===\n");
            sb.append("国家代码,国家名称,增长率\n");
            List<com.bri.model.HotspotCountry> hotspots = engine.getHotspots(5);
            for (com.bri.model.HotspotCountry h : hotspots) {
                com.bri.model.Country c = engine.getCountry(h.getCountryCode());
                String name = c != null ? c.getNameCn() : h.getCountryCode();
                sb.append(h.getCountryCode()).append(",").append(name).append(",")
                  .append(String.format("%.2f%%", h.getGrowthRate())).append("\n");
            }

            // 4) 风险预警
            sb.append("\n=== 4. 风险预警 TOP5 ===\n");
            sb.append("国家代码,国家名称,风险等级\n");
            List<com.bri.model.RiskRanking> risks = engine.getRiskRankings(5);
            for (com.bri.model.RiskRanking r : risks) {
                com.bri.model.Country c = engine.getCountry(r.getCountryCode());
                String name = c != null ? c.getNameCn() : r.getCountryCode();
                sb.append(r.getCountryCode()).append(",").append(name).append(",")
                  .append(r.getRiskLevel()).append("\n");
            }

            // 5) 聚类结果
            sb.append("\n=== 5. 国家聚类结果 ===\n");
            sb.append("国家代码,国家名称,聚类ID,聚类标签\n");
            List<com.bri.model.ClusterResult> clusters = engine.getClusters();
            for (com.bri.model.ClusterResult cl : clusters) {
                com.bri.model.Country c = engine.getCountry(cl.getCountryCode());
                String name = c != null ? c.getNameCn() : cl.getCountryCode();
                sb.append(cl.getCountryCode()).append(",").append(name).append(",")
                  .append(cl.getClusterId()).append(",").append(cl.getClusterLabel()).append("\n");
            }

            // 6) 国家月度数据明细（首列国家信息 + 各月合作指数）
            sb.append("\n=== 6. 各国月度合作指数 ===\n");
            sb.append("国家代码,国家名称,区域");
            // 月份表头
            for (int m = 1; m <= 12; m++) {
                sb.append(",").append(year).append("-").append(String.format("%02d", m));
            }
            sb.append("\n");
            for (com.bri.model.Country c : engine.getCountries()) {
                sb.append(c.getCameoCode()).append(",").append(c.getNameCn()).append(",");
                com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
                sb.append(sr != null ? sr.getNameCn() : "").append(",");
                // 各月指数
                for (int m = 1; m <= 12; m++) {
                    double idx = engine.getCoopIndex(c.getCameoCode(), year, m);
                    sb.append(String.format("%.2f", idx));
                    if (m < 12) sb.append(",");
                }
                sb.append("\n");
            }

        } catch (Exception e) {
            sb.append("\n[数据导出异常: ").append(e.getMessage()).append("]\n");
        }
        return sb.toString();
    }

    /**
     * 构建 Excel 导出内容——实际为 TSV 格式，兼容 Excel 打开
     */
    private String buildExcelContent() {
        StringBuilder sb = new StringBuilder();
        try {
            com.bri.engine.BriAnalysisEngine engine = com.yourteam.bri.service.BriEngineHolder.getEngine();
            int year = java.time.LocalDate.now().getYear();
            int month = java.time.LocalDate.now().getMonthValue();

            sb.append("=== 一带一路合作态势分析报告 ===\n");
            sb.append("生成时间: ").append(new Date()).append("\n\n");

            sb.append("=== 1. 总体统计 ===\n");
            com.bri.model.DashboardStats stats = engine.getDashboardStats();
            sb.append("国家总数\t").append(stats.getTotalCountries()).append("\n");
            sb.append("合作事件数\t").append(stats.getTotalCooperationEvents()).append("\n");
            sb.append("冲突事件数\t").append(stats.getTotalConflictEvents()).append("\n");
            sb.append("平均合作指数\t").append(String.format("%.2f", stats.getAvgCooperationIndex())).append("\n");
            sb.append("平均冲突比率\t").append(String.format("%.2f", stats.getAvgConflictRatio())).append("\n");
            sb.append("平均语调(avgTone)\t").append(String.format("%.2f", stats.getAvgTone())).append("\n\n");

            sb.append("=== 2. 合作指数排名 ===\n");
            sb.append("排名\t国家代码\t国家名称\t合作指数\t区域\n");
            List<Map.Entry<String, Double>> ranks = engine.getCoopRanking(year, month);
            int r = 1;
            for (Map.Entry<String, Double> e : ranks) {
                com.bri.model.Country c = engine.getCountry(e.getKey());
                String name = c != null ? c.getNameCn() : e.getKey();
                String region = "";
                if (c != null) {
                    com.bri.model.Subregion sr = com.bri.model.Subregion.fromKey(c.getSubregion());
                    region = sr != null ? sr.getNameCn() : "";
                }
                sb.append(r).append("\t").append(e.getKey()).append("\t").append(name).append("\t")
                  .append(String.format("%.4f", e.getValue())).append("\t").append(region).append("\n");
                r++;
            }

            sb.append("\n=== 3. 热点国家 TOP5 ===\n");
            sb.append("国家代码\t国家名称\t增长率\n");
            for (com.bri.model.HotspotCountry h : engine.getHotspots(5)) {
                com.bri.model.Country c = engine.getCountry(h.getCountryCode());
                String name = c != null ? c.getNameCn() : h.getCountryCode();
                sb.append(h.getCountryCode()).append("\t").append(name).append("\t")
                  .append(String.format("%.2f%%", h.getGrowthRate())).append("\n");
            }

            sb.append("\n=== 4. 风险预警 TOP5 ===\n");
            sb.append("国家代码\t国家名称\t风险等级\n");
            for (com.bri.model.RiskRanking rk : engine.getRiskRankings(5)) {
                com.bri.model.Country c = engine.getCountry(rk.getCountryCode());
                String name = c != null ? c.getNameCn() : rk.getCountryCode();
                sb.append(rk.getCountryCode()).append("\t").append(name).append("\t")
                  .append(rk.getRiskLevel()).append("\n");
            }

            sb.append("\n=== 5. 国家聚类结果 ===\n");
            sb.append("国家代码\t国家名称\t聚类ID\t聚类标签\n");
            for (com.bri.model.ClusterResult cl : engine.getClusters()) {
                com.bri.model.Country c = engine.getCountry(cl.getCountryCode());
                String name = c != null ? c.getNameCn() : cl.getCountryCode();
                sb.append(cl.getCountryCode()).append("\t").append(name).append("\t")
                  .append(cl.getClusterId()).append("\t").append(cl.getClusterLabel()).append("\n");
            }

        } catch (Exception e) {
            sb.append("\n[数据导出异常: ").append(e.getMessage()).append("]\n");
        }
        return sb.toString();
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择 GDELT 数据文件");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("GDELT 数据文件 (*.export.CSV, *.tsv, *.csv)", "*.export.CSV", "*.export.csv", "*.tsv", "*.csv"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            selectedFile = file;
            filePathLabel.setText(file.getAbsolutePath());
            filePathLabel.setTextFill(Color.web("#1e1e1e"));
        }
    }

    private void startRealImport() {
        if (selectedFile == null || !selectedFile.exists()) {
            showAlert("提示", "请先选择一个有效的 GDELT 数据文件");
            return;
        }

        if (isImporting.get()) return;
        isImporting.set(true);
        startImportBtn.setDisable(true);
        stopBtn.setDisable(false);

        importStatusLabel.setText("\u23F3 正在准备数据库...");
        importStatusLabel.setTextFill(Color.web("#3498db"));
        progressBar.setProgress(0.05);
        progressLabel.setText("5%");

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                Platform.runLater(() -> importStatusLabel.setText("\u23F3 步骤1/5: 初始化数据库..."));
                DataSource ds = DataSource.getInstance(DB_PATH);
                ds.initIfAbsent();

                Platform.runLater(() -> {
                    importStatusLabel.setText("\u23F3 步骤2/5: 加载国家配置...");
                    progressBar.setProgress(0.15);
                    progressLabel.setText("15%");
                });
                try {
                    new CountryConfigLoader().loadToDatabase(ds);
                } catch (Exception ignored) {}

                Platform.runLater(() -> {
                    importStatusLabel.setText("\u23F3 步骤3/5: 写入数据库...");
                    progressBar.setProgress(0.25);
                    progressLabel.setText("25%");
                });

                // 读取用户选择的 BRI 过滤模式
                String selectedFilter = filterCombo.getValue();
                GdeltImporter.BriFilterMode filterMode;
                if ("双边均为一带一路国家".equals(selectedFilter)) {
                    filterMode = GdeltImporter.BriFilterMode.BOTH;
                } else if ("全部国家（不过滤）".equals(selectedFilter)) {
                    filterMode = GdeltImporter.BriFilterMode.NONE;
                } else {
                    filterMode = GdeltImporter.BriFilterMode.ANY; // 默认：至少一方
                }

                GdeltImporter importer = new GdeltImporter();
                ImportResult importResult = importer.importFile(
                    selectedFile.toPath(),
                    ds,
                    pct -> Platform.runLater(() -> {
                        double dbProgress = 0.25 + pct * 0.50;
                        progressBar.setProgress(dbProgress);
                        progressLabel.setText(String.format("%.0f%%", dbProgress * 100));
                        importStatusLabel.setText("\u23F3 步骤3/5: 写入数据库 " + (int)(pct * 100) + "%");
                    }),
                    filterMode
                );

                Platform.runLater(() -> {
                    importStatusLabel.setText("\u23F3 步骤4/5: 加载到引擎内存...");
                    progressBar.setProgress(0.80);
                    progressLabel.setText("80%");
                });

                BriEngineHolder.loadFile(selectedFile.getAbsolutePath());

                try {
                    com.bri.dao.DaoFactory.getInstance(DB_PATH);
                } catch (Exception ignored) {}

                long elapsed = System.currentTimeMillis() - startTime;

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressLabel.setText("100%");

                    String summary = String.format(
                        "\u2705 导入完成！\n" +
                        "  写入数据库: %d 条事件\n" +
                        "  引擎索引: %d 个国家\n" +
                        "  耗时: %dms\n" +
                        "  \uD83D\uDCC1 数据库文件: %s",
                        importResult.getImportedRows(),
                        importResult.getParsedRows(),
                        elapsed,
                        new File(DB_PATH).getAbsolutePath()
                    );
                    importStatusLabel.setText(summary);
                    importStatusLabel.setTextFill(Color.web("#2ecc71"));

                    DataChangeNotifier.notifyDataChanged();

                    isImporting.set(false);
                    startImportBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    importStatusLabel.setText("\u274C 导入失败: " + ex.getMessage());
                    importStatusLabel.setTextFill(Color.web("#e74c3c"));
                    progressBar.setProgress(0);
                    progressLabel.setText("0%");
                    isImporting.set(false);
                    startImportBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });
            }
        }).start();
    }

    private void stopImport() {
        if (isImporting.compareAndSet(true, false)) {
            importStatusLabel.setText("\u23F9 导入已停止");
            importStatusLabel.setTextFill(Color.web("#e74c3c"));
            progressBar.setProgress(0);
            progressLabel.setText("0%");
            startImportBtn.setDisable(false);
            stopBtn.setDisable(true);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
