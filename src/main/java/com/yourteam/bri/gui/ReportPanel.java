package com.yourteam.bri.gui;

import com.bri.report.AnnualReportGenerator;
import com.bri.report.AnnualReportGenerator.ReportData;
import com.yourteam.bri.util.BRICountries;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.config.DataSource;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.*;

/**
 * 年度报告面板 - F-24 年度报告生成
 * <p>
 * 提供图形界面，让用户选择国家、年份，一键生成一带一路合作年度分析报告。
 * 报告以 HTML 格式保存，可在浏览器中直接打开查看。
 * </p>
 */
public class ReportPanel extends VBox {

    private ComboBox<String> countryCombo;
    private ComboBox<Integer> yearCombo;
    private TextArea reportPreview;
    private Button generateBtn;
    private Button exportBtn;
    private Button openBtn;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;

        private String lastGeneratedHtml;
    private File lastGeneratedFile;
    private ReportData lastReportData;
    private Integer lastYear;

    /** 一带一路国家代码列表（排除中国） */
    private final List<String> briCountryCodes = new ArrayList<>();

    public ReportPanel() {
        // 初始化一带一路国家代码列表（排除中国）
        Set<String> briSet = new HashSet<>();
        for (String region : BRICountries.getMainRegions()) {
            briSet.addAll(BRICountries.getCountriesByRegion(region));
        }
        for (String code : briSet) {
            if (!"CHN".equals(code)) {
                briCountryCodes.add(code);
            }
        }

        setPadding(new Insets(20));
        setSpacing(15);
        setStyle("-fx-background-color: #f5f7fa;");

        // 标题
        Label titleLabel = new Label("📄 年度合作报告生成");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setStyle("-fx-text-fill: #1a5276;");

        Label descLabel = new Label("选择国家和年份，自动生成一带一路合作年度分析报告（HTML 格式）");
        descLabel.setFont(Font.font("Microsoft YaHei", 12));
        descLabel.setStyle("-fx-text-fill: #666;");

        // 控制区
        HBox controlRow = new HBox(12);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        controlRow.setPadding(new Insets(10, 0, 10, 0));

        Label countryLabel = new Label("选择国家:");
        countryLabel.setFont(Font.font("Microsoft YaHei", 12));

        countryCombo = new ComboBox<>();
        countryCombo.setEditable(false);
        countryCombo.setPrefWidth(200);
        countryCombo.setStyle("-fx-font-size: 12px;");

        // 先添加一个占位提示
        countryCombo.getItems().add("正在检测可用数据...");

        Label yearLabel = new Label("年份:");
        yearLabel.setFont(Font.font("Microsoft YaHei", 12));

        yearCombo = new ComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear; y >= 2018; y--) {
            yearCombo.getItems().add(y);
        }
        yearCombo.setValue(currentYear);
        yearCombo.setPrefWidth(90);
        yearCombo.setStyle("-fx-font-size: 12px;");

        generateBtn = new Button("🚀 生成报告");
        generateBtn.setStyle(
            "-fx-font-size: 13px; -fx-background-color: #2980b9; -fx-text-fill: white; " +
            "-fx-padding: 8 24; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;"
        );
        generateBtn.setOnAction(e -> generateReport());

        controlRow.getChildren().addAll(countryLabel, countryCombo, yearLabel, yearCombo, generateBtn);

        // 状态行
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        statusLabel = new Label("正在检测各国数据...");
        statusLabel.setFont(Font.font("Microsoft YaHei", 11));
        statusLabel.setStyle("-fx-text-fill: #888;");

        statusRow.getChildren().addAll(progressIndicator, statusLabel);

        // 导出按钮行
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        exportBtn = new Button("💾 导出 HTML");
        exportBtn.setStyle(
            "-fx-font-size: 12px; -fx-background-color: #27ae60; -fx-text-fill: white; " +
            "-fx-padding: 6 18; -fx-background-radius: 5; -fx-cursor: hand;"
        );
        exportBtn.setDisable(true);
        exportBtn.setOnAction(e -> exportHtml());

        openBtn = new Button("🌐 在浏览器中打开");
        openBtn.setStyle(
            "-fx-font-size: 12px; -fx-background-color: #e67e22; -fx-text-fill: white; " +
            "-fx-padding: 6 18; -fx-background-radius: 5; -fx-cursor: hand;"
        );
        openBtn.setDisable(true);
        openBtn.setOnAction(e -> openInBrowser());

        actionRow.getChildren().addAll(exportBtn, openBtn);

        // 报告预览区
        Label previewLabel = new Label("报告预览");
        previewLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        previewLabel.setStyle("-fx-text-fill: #333;");

        reportPreview = new TextArea();
        reportPreview.setEditable(false);
        reportPreview.setPrefHeight(400);
        reportPreview.setStyle(
            "-fx-font-family: 'Microsoft YaHei', 'PingFang SC', monospace; " +
            "-fx-font-size: 12px; -fx-control-inner-background: #fafafa;"
        );
        reportPreview.setWrapText(true);

        getChildren().addAll(titleLabel, descLabel, controlRow, statusRow, actionRow, previewLabel, reportPreview);

        // 后台检测有数据的一带一路国家
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    // 确保数据库可用
                    ensureDatabaseReady();
                                        DaoFactory df = DaoFactory.getInstance();
                    EventDao eventDao = df.eventDao();
                    String from = com.yourteam.bri.service.DataService.getActiveFrom().toString();
                    String to = com.yourteam.bri.service.DataService.getActiveTo().toString();

                    Set<String> hasData = new HashSet<>();
                    for (String code : briCountryCodes) {
                        try {
                            List<?> t = eventDao.monthlyBilateralTrend(code, from, to);
                            if (t != null && !t.isEmpty()) {
                                hasData.add(code);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                                        Platform.runLater(() -> {
                        countryCombo.getItems().clear();
                        List<String> validItems = new ArrayList<>();
                        for (String code : briCountryCodes) {
                            if (hasData.contains(code)) {
                                String name = BRICountries.getCountryName(code);
                                validItems.add(name + " (" + code + ")");
                            }
                        }
                        // 按中文拼音首字母排序
                        Collator collator = Collator.getInstance(java.util.Locale.CHINESE);
                        validItems.sort((a, b) -> {
                            // 提取中文名部分排序
                            String nameA = a.replaceAll("\\(.*\\)", "").trim();
                            String nameB = b.replaceAll("\\(.*\\)", "").trim();
                            return collator.compare(nameA, nameB);
                        });
                        countryCombo.getItems().addAll(validItems);

                        if (!validItems.isEmpty()) {
                            // 默认选择巴基斯坦
                            for (String item : validItems) {
                                if (item.contains("PAK")) {
                                    countryCombo.setValue(item);
                                    break;
                                }
                            }
                            if (countryCombo.getValue() == null) {
                                countryCombo.setValue(validItems.get(0));
                            }
                            statusLabel.setText("就绪 — " + validItems.size() + " 个一带一路国家有可用数据");
                        } else {
                            statusLabel.setText("⚠ 所有一带一路国家均无数据库数据");
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[ReportPanel] 数据检测异常: " + e.getMessage());
                    Platform.runLater(() -> {
                        statusLabel.setText("❌ 数据检测失败: " + e.getMessage());
                        countryCombo.getItems().clear();
                        countryCombo.getItems().add("数据库不可用");
                    });
                }
            }).start();
        });
    }

    /**
     * 生成年度报告
     */
    private void generateReport() {
        String selected = countryCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            showAlert("请先选择一个国家");
            return;
        }

        Integer year = yearCombo.getValue();
        if (year == null) {
            showAlert("请选择一个年份");
            return;
        }

        // 解析国家代码
        String countryCode = extractCode(selected);
        if (countryCode == null) {
            showAlert("无法识别国家代码，请从下拉列表中选择");
            return;
        }

                        final String countryName = BRICountries.getCountryName(countryCode);
        final String countryDisplay = countryName != null ? countryName : countryCode;

        // 开始生成
        generateBtn.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("⏳ 正在生成 " + countryDisplay + " " + year + " 年度报告...");

                Platform.runLater(() -> {
            try {
                AnnualReportGenerator generator = new AnnualReportGenerator();
                final ReportData rd = generator.generate(countryCode, year);
                String html = rd != null ? generator.toHtml(rd) : "<html><body><h2>暂无数据，无法生成报告</h2></body></html>";
                lastGeneratedHtml = html;

                // 预览区显示纯文本摘要
                reportPreview.setText(rd != null
                    ? "【" + rd.countryName + " " + rd.year + "年度报告摘要】\n\n"
                    + "总事件数: " + rd.totalEvents + "\n"
                    + "合作事件: " + rd.coopEvents + "\n"
                    + "冲突事件: " + rd.conflictEvents + "\n"
                    + "合作占比: " + String.format("%.1f%%", rd.coopRatio) + "\n"
                    + "平均Goldstein: " + String.format("%.2f", rd.avgGoldstein) + "\n"
                    + "平均合作指数: " + String.format("%.4f", rd.avgIndex) + "\n"
                    + "趋势方向: " + rd.trendDirection + "\n"
                    + "风险等级: " + rd.riskLevel
                    : "数据库暂无该国该年数据，请尝试其他年份或国家。");
                statusLabel.setText("✅ 报告生成完成！" + countryDisplay + " " + year + " 年");
                exportBtn.setDisable(false);
                openBtn.setDisable(false);

            } catch (Exception ex) {
                reportPreview.setText("生成报告时出错:\n" + ex.getMessage());
                statusLabel.setText("❌ 生成失败: " + ex.getMessage());
                exportBtn.setDisable(true);
                openBtn.setDisable(true);
            } finally {
                generateBtn.setDisable(false);
                progressIndicator.setVisible(false);
            }
        });
    }

    /**
     * 导出 HTML 文件
     */
    private void exportHtml() {
        if (lastGeneratedHtml == null) {
            showAlert("请先生成报告");
            return;
        }

        String selected = countryCombo.getValue();
        Integer year = yearCombo.getValue();
        String countryCode = extractCode(selected);
        String countryName = countryCode != null ? BRICountries.getCountryName(countryCode) : "未知";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存年度报告");
        fileChooser.setInitialFileName("bri_" + countryCode + "_" + year + "_annual_report.html");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("HTML 文件 (*.html)", "*.html"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), lastGeneratedHtml, java.nio.charset.StandardCharsets.UTF_8);
                lastGeneratedFile = file;
                showAlert("✅ 报告已导出到:\n" + file.getAbsolutePath());
                statusLabel.setText("✅ 已导出: " + file.getName());
                openBtn.setDisable(false);
            } catch (IOException ex) {
                showAlert("❌ 导出失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 在浏览器中打开
     */
    private void openInBrowser() {
        if (lastGeneratedHtml == null) return;

        // 如果没有导出，先保存到临时文件
        if (lastGeneratedFile == null || !lastGeneratedFile.exists()) {
            try {
                File tempFile = File.createTempFile("bri_report_", ".html");
                tempFile.deleteOnExit();
                java.nio.file.Files.writeString(tempFile.toPath(), lastGeneratedHtml, java.nio.charset.StandardCharsets.UTF_8);
                lastGeneratedFile = tempFile;
            } catch (IOException ex) {
                showAlert("❌ 无法创建临时文件: " + ex.getMessage());
                return;
            }
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(lastGeneratedFile.toURI());
            } else {
                // 回退：使用 Runtime
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + lastGeneratedFile.getAbsolutePath());
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open " + lastGeneratedFile.getAbsolutePath());
                } else {
                    Runtime.getRuntime().exec("xdg-open " + lastGeneratedFile.getAbsolutePath());
                }
            }
        } catch (IOException ex) {
            showAlert("❌ 无法打开浏览器: " + ex.getMessage());
        }
    }

    /**
     * 从下拉选项文本中提取国家代码
     */
    private String extractCode(String text) {
        if (text == null) return null;
        int start = text.lastIndexOf('(');
        int end = text.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return text.substring(start + 1, end).trim();
        }
        // 如果本身就是代码
        if (text.length() == 3 && text.equals(text.toUpperCase())) {
            return text;
        }
        return null;
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

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("年度报告");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}