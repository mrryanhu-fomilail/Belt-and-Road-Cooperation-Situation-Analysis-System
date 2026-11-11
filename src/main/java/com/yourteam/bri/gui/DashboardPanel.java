package com.yourteam.bri.gui;

import com.yourteam.bri.model.ClusterResult;
import com.yourteam.bri.model.CooperationIndex;
import com.yourteam.bri.service.DataService;
import com.yourteam.bri.util.BRICountries;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘面板 - 合作态势总览仪表盘，作为应用首页展示核心概览数据
 * <p>
 * 功能覆盖（对应需求 F-18）：
 * <ul>
 *   <li>关键指标卡片：总国家数、合作事件量、冲突事件量、合作占比、平均情感语调</li>
 *   <li>合作热点 TOP5：按综合合作指数排名展示最紧密的伙伴国家（数据源：DataService.getCooperationRankings()）</li>
 *   <li>风险预警 TOP5：按风险评分排序展示高风险国家（数据源：DataService.getClusterResults()）</li>
 *   <li>区域筛选：支持按区域（东亚、东南亚、中亚、南亚、中东、欧洲、非洲等）过滤，仅展示一带一路国家数据</li>
 * </ul>
 * </p>
 * <p>
 * 数据来源：概览卡片来自 DataService.getDashboardData()，合作热点来自 getCooperationRankings()，
 * 风险预警来自 getClusterResults()，确保各指标与其他面板（RankingPanel、ClusteringPanel）展示一致。
 * 区域筛选逻辑参考 RankingPanel.updateRanking() 实现（ComboBox 下拉框 + stream 过滤）。
 * </p>
 *
 * @see com.yourteam.bri.service.DataService#getDashboardData() 概览数据源
 * @see com.yourteam.bri.service.DataService#getCooperationRankings() 合作热点数据源
 * @see com.yourteam.bri.service.DataService#getClusterResults() 风险预警数据源
 */
public class DashboardPanel extends ScrollPane {

    private ComboBox<String> regionFilter;
    private HBox cardsRow;
    private VBox hotBox;
    private VBox riskBox;

    public DashboardPanel() {
        setFitToWidth(true);
        setStyle("-fx-background: #f5f7fa;");

        VBox container = new VBox(20);
        container.setPadding(new Insets(20));

        // 标题
        Label titleLabel = new Label("\uD83D\uDCCA 合作态势总览仪表盘");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        // 控制栏 - 区域筛选（参考 RankingPanel 实现）
        HBox controlBar = new HBox(15);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(10));
        controlBar.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label filterLabel = new Label("\uD83C\uDF0F 区域筛选:");
        filterLabel.setFont(Font.font("Microsoft YaHei", 13));

        regionFilter = new ComboBox<>();
        regionFilter.setPrefWidth(160);
        regionFilter.setStyle("-fx-font-size: 13px;");
        regionFilter.getItems().add("全部一带一路国家");
        for (String region : new String[]{"东亚", "东南亚", "中亚", "南亚", "中东", "欧洲", "非洲", "北美洲", "南美洲", "大洋洲"}) {
            regionFilter.getItems().add(region);
        }
        regionFilter.getSelectionModel().select(0);
        regionFilter.setOnAction(e -> refreshDashboard());

        Label hintLabel = new Label("\uD83D\uDCA1 仅展示一带一路沿线国家数据");
        hintLabel.setFont(Font.font("Microsoft YaHei", 11));
        hintLabel.setTextFill(Color.GRAY);

        controlBar.getChildren().addAll(filterLabel, regionFilter, hintLabel);

        // 概览数据卡片（初始占位，后续在 refreshDashboard 中填充）
        cardsRow = new HBox(15);
        cardsRow.setAlignment(Pos.CENTER);

        // 热点与风险 - 带独立标题的外层容器
        hotBox = new VBox(10);
        hotBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 3);");
        hotBox.setPadding(new Insets(15));

        riskBox = new VBox(10);
        riskBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 3);");
        riskBox.setPadding(new Insets(15));

        HBox hotAndRiskRow = new HBox(20);
        hotAndRiskRow.setPrefHeight(280);
        hotAndRiskRow.getChildren().addAll(hotBox, riskBox);
        HBox.setHgrow(hotBox, Priority.ALWAYS);
        HBox.setHgrow(riskBox, Priority.ALWAYS);

        // 说明
        Label infoLabel = new Label("\uD83D\uDCA1 提示：点击左侧菜单栏切换不同分析模块");
        infoLabel.setFont(Font.font("Microsoft YaHei", 12));
        infoLabel.setTextFill(Color.GRAY);

        container.getChildren().addAll(titleLabel, controlBar, cardsRow, hotAndRiskRow, infoLabel);
        setContent(container);

        // 初始加载数据
        refreshDashboard();
    }

    /**
     * 刷新仪表盘数据 - 根据当前区域筛选条件重新加载所有卡片
     * <p>
     * 概览卡片使用 DataService.getDashboardData()，
     * 合作热点使用 DataService.getCooperationRankings()（与 RankingPanel 一致），
     * 风险预警使用 DataService.getClusterResults()（与 ClusteringPanel 一致）。
     * </p>
     */
    private void refreshDashboard() {
        String selectedRegion = regionFilter.getValue();
        Map<String, Object> data = DataService.getDashboardData();
        if (data == null) data = new java.util.LinkedHashMap<>();

        // 更新顶部概览卡片
        updateOverviewCards(data);

        // 更新合作热点 TOP5 — 使用合作指数排名（与 RankingPanel 数据源一致）
        updateHotTopics(selectedRegion);

        // 更新风险预警 TOP5 — 使用聚类风险评分（与 ClusteringPanel 数据源一致）
        updateRiskWarnings(selectedRegion);
    }

    // ==================== 概览卡片 ====================

    /**
     * 更新顶部概览数据卡片行
     */
    private void updateOverviewCards(Map<String, Object> data) {
        cardsRow.getChildren().clear();

        cardsRow.getChildren().add(createStatCard("\uD83C\uDF0D", "沿线国家",
            safeStr(data.get("totalCountries")), "涵盖国家总数", "#3498db"));
        cardsRow.getChildren().add(createStatCard("\uD83E\uDD1D", "合作事件",
            formatNumber(safeInt(data.get("cooperationEvents"))), "合作类事件总量", "#2ecc71"));
        cardsRow.getChildren().add(createStatCard("\u26A1", "冲突事件",
            formatNumber(safeInt(data.get("conflictEvents"))), "冲突类事件总量", "#e74c3c"));
        cardsRow.getChildren().add(createStatCard("\uD83D\uDCC8", "合作占比",
            safeStr(data.get("cooperationRatio")) + "%", "合作事件占比", "#9b59b6"));
        cardsRow.getChildren().add(createStatCard("\uD83D\uDCAC", "平均情感",
            String.format("%.1f", safeDouble(data.get("avgTone"))), "媒体情感语调均值", "#f39c12"));
    }

    // ==================== 合作热点 TOP5（与 RankingPanel 数据一致）====================

    /**
     * 更新合作热点 TOP5
     * <p>
     * 使用 DataService.getCooperationRankings() 获取合作指数排名（与 RankingPanel 一致），
     * 按区域筛选后取前 5 名，展示综合合作指数。
     * </p>
     */
    private void updateHotTopics(String selectedRegion) {
        hotBox.getChildren().clear();

        // ---- 区域标题行 ----
        VBox headerArea = buildHeaderArea("\uD83D\uDD25 合作热点 TOP5", "#2ecc71");
        hotBox.getChildren().add(headerArea);

        // ---- 获取数据源（与 RankingPanel 一致）----
        List<CooperationIndex> allRankings = DataService.getCooperationRankings();
        if (allRankings == null) allRankings = new ArrayList<>();

        // 过滤：仅保留一带一路国家 + 按区域筛选
        List<CooperationIndex> filtered = allRankings.stream()
            .filter(r -> r.getCountryCode() != null && BRICountries.isBriCountry(r.getCountryCode()))
            .filter(r -> {
                if (selectedRegion == null || selectedRegion.equals("全部一带一路国家")) {
                    return true;
                }
                return selectedRegion.equals(r.getRegion());
            })
            .sorted((a, b) -> Double.compare(b.getIndexValue(), a.getIndexValue()))
            .limit(5)
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            hotBox.getChildren().add(createEmptyLabel());
            return;
        }

        // 生成条目列表
        for (int i = 0; i < filtered.size(); i++) {
            CooperationIndex ci = filtered.get(i);
            HBox itemRow = new HBox(10);
            itemRow.setAlignment(Pos.CENTER_LEFT);

            // 排名编号（金银铜色）
            Label rankLabel = new Label(String.format("%02d", i + 1));
            rankLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            String rankColor = i == 0 ? "#FFD700" : (i < 3 ? "#C0C0C0" : "#CD7F32");
            rankLabel.setStyle("-fx-text-fill: " + rankColor + ";");

            // 国家名称（带代码）
            Label nameLabel = new Label(ci.getCountryName() + " (" + ci.getCountryCode() + ")");
            nameLabel.setFont(Font.font("Microsoft YaHei", 13));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 合作指数值（与 RankingPanel 展示格式一致）
            Label valueLabel = new Label(String.format("%.4f", ci.getIndexValue()));
            valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            valueLabel.setStyle("-fx-text-fill: #2ecc71;");

            itemRow.getChildren().addAll(rankLabel, nameLabel, spacer, valueLabel);

            // 分隔线
            if (i < filtered.size() - 1) {
                VBox itemContainer = new VBox(5);
                itemContainer.getChildren().add(itemRow);
                Region separator = new Region();
                separator.setPrefHeight(1);
                separator.setStyle("-fx-background-color: #e8e8e8;");
                itemContainer.getChildren().add(separator);
                hotBox.getChildren().add(itemContainer);
            } else {
                hotBox.getChildren().add(itemRow);
            }
        }
    }

    // ==================== 风险预警 TOP5（与 ClusteringPanel 数据一致）====================

    /**
     * 更新风险预警 TOP5
     * <p>
     * 使用 DataService.getClusterResults() 获取聚类风险评分（与 ClusteringPanel 一致），
     * 按区域筛选后取风险最高的前 5 名，展示风险等级和评分。
     * </p>
     */
    private void updateRiskWarnings(String selectedRegion) {
        riskBox.getChildren().clear();

        // ---- 区域标题行 ----
        VBox headerArea = buildHeaderArea("\u26A0 风险预警 TOP5", "#e74c3c");
        riskBox.getChildren().add(headerArea);

        // ---- 获取数据源（与 ClusteringPanel 一致）----
        List<ClusterResult> allClusters = DataService.getClusterResults();
        if (allClusters == null) allClusters = new ArrayList<>();

        // 过滤：仅保留一带一路国家 + 按区域筛选
        List<ClusterResult> filtered = allClusters.stream()
            .filter(r -> r.getCountryCode() != null && BRICountries.isBriCountry(r.getCountryCode()))
            .filter(r -> {
                if (selectedRegion == null || selectedRegion.equals("全部一带一路国家")) {
                    return true;
                }
                String region = BRICountries.getCountryRegion(r.getCountryCode());
                return selectedRegion.equals(region);
            })
            .sorted((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()))
            .limit(5)
            .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            riskBox.getChildren().add(createEmptyLabel());
            return;
        }

        // 生成条目列表
        for (int i = 0; i < filtered.size(); i++) {
            ClusterResult cr = filtered.get(i);
            HBox itemRow = new HBox(10);
            itemRow.setAlignment(Pos.CENTER_LEFT);

            // 排名编号（高风险红色标注）
            Label rankLabel = new Label(String.format("%02d", i + 1));
            rankLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            String rankColor = i < 2 ? "#e74c3c" : "#f39c12";
            rankLabel.setStyle("-fx-text-fill: " + rankColor + ";");

            // 国家名称（带代码）
            Label nameLabel = new Label(cr.getCountryName() + " (" + cr.getCountryCode() + ")");
            nameLabel.setFont(Font.font("Microsoft YaHei", 13));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 风险等级 + 评分（与 ClusteringPanel 展示风格一致）
            Label riskLabel = new Label(cr.getRiskLevel() + " (" + String.format("%.0f", cr.getRiskScore()) + "分)");
            riskLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));
            String riskTextColor = cr.getRiskLevel().contains("高") ? "#e74c3c"
                : cr.getRiskLevel().contains("中") ? "#f39c12" : "#808080";
            riskLabel.setStyle("-fx-text-fill: " + riskTextColor + ";");

            itemRow.getChildren().addAll(rankLabel, nameLabel, spacer, riskLabel);

            // 分隔线
            if (i < filtered.size() - 1) {
                VBox itemContainer = new VBox(5);
                itemContainer.getChildren().add(itemRow);
                Region separator = new Region();
                separator.setPrefHeight(1);
                separator.setStyle("-fx-background-color: #e8e8e8;");
                itemContainer.getChildren().add(separator);
                riskBox.getChildren().add(itemContainer);
            } else {
                riskBox.getChildren().add(itemRow);
            }
        }
    }

    // ==================== 公共 UI 构建方法 ====================

    /**
     * 构建区域标题行（标题 + 装饰线）
     */
    private VBox buildHeaderArea(String title, String accentColorHex) {
        Label headerLabel = new Label(title);
        headerLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        headerLabel.setTextFill(Color.web("#1e1e3c"));

        Region headerLine = new Region();
        headerLine.setPrefHeight(3);
        headerLine.setPrefWidth(60);
        headerLine.setStyle("-fx-background-color: " + accentColorHex + "; -fx-background-radius: 2;");

        VBox headerArea = new VBox(5);
        headerArea.getChildren().addAll(headerLabel, headerLine);
        return headerArea;
    }

    /**
     * 创建空数据占位标签
     */
    private Label createEmptyLabel() {
        Label emptyLabel = new Label("暂无数据");
        emptyLabel.setFont(Font.font("Microsoft YaHei", 13));
        emptyLabel.setTextFill(Color.LIGHTGRAY);
        emptyLabel.setPadding(new Insets(20, 0, 20, 0));
        return emptyLabel;
    }

    // ==================== 统计卡片 ====================

    /**
     * 创建单个统计卡片组件
     */
    private VBox createStatCard(String icon, String title, String value, String desc, String accentColorHex) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setPrefWidth(180);
        card.setPrefHeight(140);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 3);");

        Label iconLabel = new Label(icon);
        iconLabel.setFont(new Font(28));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Microsoft YaHei", 12));
        titleLabel.setTextFill(Color.GRAY);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 22));
        valueLabel.setStyle("-fx-text-fill: " + accentColorHex + ";");

        Label descLabel = new Label(desc);
        descLabel.setFont(Font.font("Microsoft YaHei", 10));
        descLabel.setTextFill(Color.LIGHTGRAY);

        card.getChildren().addAll(iconLabel, titleLabel, valueLabel, descLabel);
        return card;
    }

    // ==================== 工具方法 ====================

    private int safeInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private double safeDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    private String safeStr(Object v) {
        return v != null ? v.toString() : "0";
    }

    /**
     * 格式化数字为中文友好显示
     */
    private String formatNumber(int num) {
        if (num >= 10000) {
            return String.format("%.1f万", num / 10000.0);
        }
        return String.format("%,d", num);
    }

    /**
     * 将 JavaFX Color 对象转换为 16 进制颜色字符串
     */
    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
}
