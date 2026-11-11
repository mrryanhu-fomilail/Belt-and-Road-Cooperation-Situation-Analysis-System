package com.yourteam.bri.gui;

import com.bri.engine.BriAnalysisEngine;
import com.bri.model.AssociationRule;
import com.yourteam.bri.service.BriEngineHolder;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

import com.yourteam.bri.service.DataService;

/**
 * 关联规则挖掘面板 — 展示 Apriori 算法挖掘出的"事件→后续事件"关联规则（对应需求 F-17）。
 *
 * <p>功能覆盖：
 * <ul>
 *   <li>参数配置：支持度阈值、置信度阈值滑块</li>
 *   <li>规则表格：展示前项、后项、支持度、置信度、提升度、覆盖度</li>
 *   <li>规则解释：说明"当…发生后，有…概率会发生…"</li>
 *   <li>按提升度排序，高亮 lift > 1 的有效规则</li>
 * </ul>
 * </p>
 */
public class AprioriPanel extends VBox {

    private TableView<AssociationRule> ruleTable;
    private Label statusLabel;
    private Slider supportSlider;
    private Slider confidenceSlider;

    /** 上次使用的最小支持度，供 refreshFromDatabase 复用 */
    private double lastMinSupport = 0.02;
    /** 上次使用的最小置信度，供 refreshFromDatabase 复用 */
    private double lastMinConfidence = 0.50;
    /** 是否已执行过首次挖掘 */
    private boolean hasRunOnce = false;

    public AprioriPanel() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: #f5f7fa;");

        // 标题
        Label titleLabel = new Label("\uD83D\uDD17 关联规则挖掘（Apriori）");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        Label descLabel = new Label("分析\"当A国与B国发生某类事件后，后续倾向于发生C类事件\"的模式规律");
        descLabel.setFont(Font.font("Microsoft YaHei", 12));
        descLabel.setTextFill(Color.GRAY);

        // 参数控制区（VBox 两行布局）
        VBox controlBar = createControlBar();

        // 规则表格
        ruleTable = createRuleTable();

        // 底部状态 — 显示当前时间范围
        String timeInfo = "当前时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo();
        statusLabel = new Label(timeInfo);
        statusLabel.setFont(Font.font("Microsoft YaHei", 11));
        statusLabel.setTextFill(Color.web("#64748b"));

        getChildren().addAll(titleLabel, descLabel, controlBar, ruleTable, statusLabel);
    }

    /**
     * 创建控制区域（四行布局：支持度滑块→置信度滑块→按钮组→提示说明）
     */
    private VBox createControlBar() {
        VBox controlContainer = new VBox(10);
        controlContainer.setPadding(new Insets(15));
        controlContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        // ---- 支持度滑块行 ----
        HBox supportRow = new HBox(10);
        supportRow.setAlignment(Pos.CENTER_LEFT);
        Label supportLabel = new Label("最小支持度:");
        supportLabel.setFont(Font.font("Microsoft YaHei", 11));
        supportLabel.setPrefWidth(80);
        supportSlider = new Slider(0.005, 0.10, 0.02);
        supportSlider.setPrefWidth(240);
        supportSlider.setShowTickLabels(true);
        supportSlider.setMajorTickUnit(0.02);
        supportSlider.setMinorTickCount(1);
        Label supportVal = new Label("0.020");
        supportVal.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        supportVal.setPrefWidth(45);
        supportSlider.valueProperty().addListener((obs, old, val) -> {
            supportVal.setText(String.format("%.3f", val.doubleValue()));
        });
        supportRow.getChildren().addAll(supportLabel, supportSlider, supportVal);

        // 支持度滑块和置信度滑块之间留一点间距，然后右侧放按钮和提示
        // 用一个 HBox 把两个滑块放在左侧区域
        VBox slidersBox = new VBox(8);

        // ---- 置信度滑块行 ----
        HBox confidenceRow = new HBox(10);
        confidenceRow.setAlignment(Pos.CENTER_LEFT);
        Label confidenceLabel = new Label("最小置信度:");
        confidenceLabel.setFont(Font.font("Microsoft YaHei", 11));
        confidenceLabel.setPrefWidth(80);
        confidenceSlider = new Slider(0.1, 0.95, 0.50);
        confidenceSlider.setPrefWidth(240);
        confidenceSlider.setShowTickLabels(true);
        confidenceSlider.setMajorTickUnit(0.1);
        confidenceSlider.setMinorTickCount(1);
        Label confidenceVal = new Label("0.50");
        confidenceVal.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        confidenceVal.setPrefWidth(45);
        confidenceSlider.valueProperty().addListener((obs, old, val) -> {
            confidenceVal.setText(String.format("%.2f", val.doubleValue()));
        });
        confidenceRow.getChildren().addAll(confidenceLabel, confidenceSlider, confidenceVal);

        slidersBox.getChildren().addAll(supportRow, confidenceRow);

        // ---- 右侧区域：按钮组 + 提示说明 ----
        VBox rightBox = new VBox(10);
        rightBox.setAlignment(Pos.CENTER_LEFT);
        rightBox.setPadding(new Insets(0, 0, 0, 25));

        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Button mineBtn = new Button("\u26A1 执行挖掘");
        mineBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #8e44ad; -fx-text-fill: white; -fx-padding: 10px 28px; -fx-background-radius: 6; -fx-cursor: hand;");
        mineBtn.setOnAction(e -> runApriori());

        Button resetBtn = new Button("\uD83D\uDD04 重置");
        resetBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #95a5a6; -fx-text-fill: white; -fx-padding: 10px 22px; -fx-background-radius: 6; -fx-cursor: hand;");
        resetBtn.setOnAction(e -> {
            supportSlider.setValue(0.02);
            confidenceSlider.setValue(0.50);
            ruleTable.getItems().clear();
            statusLabel.setText("点击「执行挖掘」开始分析 | 时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo());
            statusLabel.setTextFill(Color.web("#64748b"));
            hasRunOnce = false;
        });

        buttonRow.getChildren().addAll(mineBtn, resetBtn);

        Label infoLabel = new Label("\uD83D\uDCA1 提升度 > 1 表示正相关，值越大关联越强");
        infoLabel.setFont(Font.font("Microsoft YaHei", 11));
        infoLabel.setTextFill(Color.web("#7f8c8d"));

        rightBox.getChildren().addAll(buttonRow, infoLabel);

        // ---- 整体布局：左侧滑块 + 右侧按钮+提示 ----
        HBox mainRow = new HBox(0);
        mainRow.setAlignment(Pos.CENTER_LEFT);
        mainRow.getChildren().addAll(slidersBox, rightBox);

        controlContainer.getChildren().add(mainRow);
        return controlContainer;
    }

    private TableView<AssociationRule> createRuleTable() {
        TableView<AssociationRule> table = new TableView<>();
        table.setPrefHeight(450);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // 序号列
        TableColumn<AssociationRule, Number> rankCol = new TableColumn<>("#");
        rankCol.setPrefWidth(40);
        rankCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(
            table.getItems().indexOf(data.getValue()) + 1
        ));
        rankCol.setSortable(false);

        // 前项列
        TableColumn<AssociationRule, String> antCol = new TableColumn<>("前项（触发事件）");
        antCol.setPrefWidth(200);
        antCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getAntecedent()));

        // 后项列
        TableColumn<AssociationRule, String> consCol = new TableColumn<>("后项（被触发事件）");
        consCol.setPrefWidth(200);
        consCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getConsequent()));

        // 支持度列
        TableColumn<AssociationRule, String> supCol = new TableColumn<>("支持度");
        supCol.setPrefWidth(80);
        supCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%.2f%%", data.getValue().getSupport() * 100)
        ));

        // 置信度列
        TableColumn<AssociationRule, String> confCol = new TableColumn<>("置信度");
        confCol.setPrefWidth(80);
        confCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%.1f%%", data.getValue().getConfidence() * 100)
        ));

        // 提升度列（带颜色）
        TableColumn<AssociationRule, String> liftCol = new TableColumn<>("提升度");
        liftCol.setPrefWidth(80);
        liftCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%.3f", data.getValue().getLift())
        ));
        liftCol.setCellFactory(col -> new TableCell<AssociationRule, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    double lift = Double.parseDouble(item);
                    if (lift > 2.0) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if (lift > 1.0) {
                        setStyle("-fx-text-fill: #2ecc71;");
                    } else {
                        setStyle("-fx-text-fill: #95a5a6;");
                    }
                }
            }
        });

        // 覆盖度列
        TableColumn<AssociationRule, String> covCol = new TableColumn<>("覆盖度");
        covCol.setPrefWidth(80);
        covCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            String.format("%.1f%%", data.getValue().getCoverage() * 100)
        ));

        table.getColumns().addAll(rankCol, antCol, consCol, supCol, confCol, liftCol, covCol);

        // 点击行显示详情
        table.setRowFactory(tv -> {
            TableRow<AssociationRule> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showRuleDetail(row.getItem());
                }
            });
            return row;
        });

        return table;
    }

    /**
     * 执行 Apriori 挖掘。
     */
    /**
     * 刷新关联规则 — 当时间轴范围变化时由 MainWindow 调用。
     * <p>使用上次挖掘的参数重新执行挖掘，以匹配新的时间范围。</p>
     * 如果从未执行过挖掘，则不执行任何操作（用户点击"执行挖掘"按钮后才开始）。
     */
    public void refreshFromDatabase() {
        if (!hasRunOnce) return;
        // 用上次的参数重新挖掘
        double oldSupport = supportSlider.getValue();
        double oldConfidence = confidenceSlider.getValue();
        supportSlider.setValue(lastMinSupport);
        confidenceSlider.setValue(lastMinConfidence);
        runApriori();
        // 恢复滑块值（runApriori 内部会更新 lastMinSupport 和 lastMinConfidence）
        // 但我们希望滑块保持用户设定的值，所以恢复
        supportSlider.setValue(oldSupport);
        confidenceSlider.setValue(oldConfidence);
    }

    private void runApriori() {
        try {
            double minSupport = supportSlider.getValue();
            double minConfidence = confidenceSlider.getValue();
            // 记录本次参数，供 refreshFromDatabase 复用
            lastMinSupport = minSupport;
            lastMinConfidence = minConfidence;
            hasRunOnce = true;

            statusLabel.setText("⏳ 正在挖掘关联规则... 时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo());
            statusLabel.setTextFill(Color.web("#2980b9"));

            BriAnalysisEngine engine = BriEngineHolder.getEngine();
            List<AssociationRule> rules = engine.getAssociationRules(minSupport, minConfidence, 3);

            ruleTable.getItems().clear();
            if (rules.isEmpty()) {
                statusLabel.setText("\u26A0\uFE0F 未找到符合条件的关联规则，请尝试降低支持度或置信度阈值");
                statusLabel.setTextFill(Color.web("#e67e22"));
                return;
            }

            // 限制最多显示前 200 条
            List<AssociationRule> display = rules.size() > 200 ? rules.subList(0, 200) : rules;
            ruleTable.getItems().addAll(display);

            String timeInfo = "\n时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo();
            statusLabel.setText(String.format("✅ 找到 %d 条规则（显示前 %d 条）| 支持度≥%.3f，置信度≥%.2f%s",
                    rules.size(), display.size(), minSupport, minConfidence, timeInfo));
            statusLabel.setTextFill(Color.web("#27ae60"));

        } catch (Exception e) {
            statusLabel.setText("❌ 挖掘失败: " + e.getMessage());
            statusLabel.setTextFill(Color.RED);
            System.err.println("[AprioriPanel] 关联规则挖掘异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 双击规则行显示详情弹窗。
     */
    private void showRuleDetail(AssociationRule rule) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("\uD83D\uDD17 关联规则详情");
        alert.setHeaderText("规则解释");

        String antecedent = rule.getAntecedent();
        String consequent = rule.getConsequent();

        String explanation = String.format(
            "\uD83D\uDCCC 规则：\n" +
            "  当「%s」发生后，\n" +
            "  有 %.1f%% 的概率会发生「%s」。\n\n" +
            "\uD83D\uDCCA 指标：\n" +
            "  • 支持度 (Support): %.2f%%\n" +
            "    （%.2f%% 的事务中同时出现了前项和后项）\n\n" +
            "  • 置信度 (Confidence): %.1f%%\n" +
            "    （前项出现时，后项也出现的概率）\n\n" +
            "  • 提升度 (Lift): %.3f\n" +
            "    （>1 表示正相关，>2 表示强关联）\n\n" +
            "  • 覆盖度 (Coverage): %.1f%%\n" +
            "    （前项出现的事务占总事务的比例）",
            antecedent,
            rule.getConfidence() * 100,
            consequent,
            rule.getSupport() * 100,
            rule.getSupport() * 100,
            rule.getConfidence() * 100,
            rule.getLift(),
            rule.getCoverage() * 100
        );

        alert.setContentText(explanation);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
}
