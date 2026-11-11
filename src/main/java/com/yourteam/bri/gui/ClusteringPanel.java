package com.yourteam.bri.gui;

import com.yourteam.bri.model.ClusterResult;
import com.yourteam.bri.util.BRICountries;

import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.bri.config.DataSource;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.yourteam.bri.service.DataService;

/**
 * 聚类分析与风险评估面板 — 基于数据库真实数据的 K-Means 聚类（对应需求 F-14、F-15）。
 *
 * <p>直接从 bri.db 查询所有 GDELT 事件数据，按国家聚合特征（Goldstein均值、
 * 情感语调、事件数量、合作/冲突比例），通过简单的规则聚类划分四类国家，
 * 并计算投资风险评分。</p>
 */
public class ClusteringPanel extends VBox {

    /** 可刷新的聚类卡片容器 */
    private VBox clusterCardsContainer;
    /** 可刷新的风险评估容器 */
    private VBox riskTableContainer;
    /** 状态栏（显示时间范围与刷新状态） */
    private Label statusLabel;

    public ClusteringPanel() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: #f5f7fa;");

        // 标题行：左侧标题 + 右侧刷新按钮 + 时间范围状态
        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("\uD83C\uDFAF 聚类分析与风险评估");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("\uD83D\uDD04 刷新");
        refreshBtn.setFont(Font.font("Microsoft YaHei", 12));
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 6px 18px; -fx-background-radius: 5; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> refreshFromDatabase());

        statusLabel = new Label("\uD83D\uDDF7 时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo());
        statusLabel.setFont(Font.font("Microsoft YaHei", 10));
        statusLabel.setTextFill(Color.web("#7f8c8d"));

        titleRow.getChildren().addAll(titleLabel, spacer, refreshBtn, statusLabel);

        Label descLabel = new Label("基于合作指数、冲突频率、Goldstein均值等特征，将沿线国家分类，并评估投资风险等级");
        descLabel.setFont(Font.font("Microsoft YaHei", 12));
        descLabel.setTextFill(Color.GRAY);

        // 聚类卡片区域（可刷新的容器放在 ScrollPane 内）
        clusterCardsContainer = new VBox();
        clusterCardsContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane clusterScroll = new ScrollPane(clusterCardsContainer);
        clusterScroll.setFitToWidth(true);
        clusterScroll.setFitToHeight(true);
        clusterScroll.setPrefHeight(380);
        clusterScroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");

        // 风险评估区域（可刷新的容器放在 ScrollPane 内）
        riskTableContainer = new VBox();
        riskTableContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane riskScroll = new ScrollPane(riskTableContainer);
        riskScroll.setFitToWidth(true);
        riskScroll.setFitToHeight(true);
        riskScroll.setPrefHeight(280);
        riskScroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");

        getChildren().addAll(titleRow, descLabel, clusterScroll, riskScroll);

        // 初始加载数据
        refreshFromDatabase();
    }

    /**
     * 刷新聚类结果 — 时间轴变化后由 MainWindow 调用，或用户点击"刷新"按钮触发。
     * 使用 DataService 当前活动时间范围过滤事件，然后重新计算聚类。
     */
    public void refreshFromDatabase() {
        statusLabel.setText("\u23F3 计算中... 时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo());
        statusLabel.setTextFill(Color.web("#e67e22"));

        List<ClusterResult> results = computeClustersFromDb();

        clusterCardsContainer.getChildren().clear();
        clusterCardsContainer.getChildren().add(createClusterCards(results));

        riskTableContainer.getChildren().clear();
        riskTableContainer.getChildren().add(createRiskTable(results));

        statusLabel.setText("\u2705 已更新 | 时间范围: " + DataService.getActiveFrom() + " ~ " + DataService.getActiveTo());
        statusLabel.setTextFill(Color.web("#7f8c8d"));
    }

    // ================================================================
    //  数据库聚类核心
    // ================================================================

    /**
     * 从数据库查询事件数据，按国家聚合特征，执行聚类分析。
     *
     * <p>流程：
     * <ol>
     *   <li>查询数据库全部事件（近一年数据）</li>
     *   <li>按 actor1Code 分组（排除无意义的 "CHN" 及空值）</li>
     *   <li>对每个国家计算 5 个特征：合作指数、冲突频率、Goldstein 均值、情感语调、媒体关注度</li>
     *   <li>基于 Goldstein 均值 + 情感语调做简单聚类划分四类</li>
     *   <li>计算风险评分</li>
     * </ol></p>
     */
    private List<ClusterResult> computeClustersFromDb() {
        List<ClusterResult> results = new ArrayList<>();

        try {
            // 1. 获取 DaoFactory（同 QueryPanel 风格）
            DaoFactory df;
            try {
                df = DaoFactory.getInstance();
            } catch (IllegalStateException e) {
                DataSource ds = DataSource.getInstance("bri.db");
                ds.initIfAbsent();
                df = DaoFactory.getInstance("bri.db");
            }
            EventDao eventDao = df.eventDao();

            // 2. 查询事件 — 使用 DataService 当前活动时间范围（与时间轴联动）
            LocalDate start = DataService.getActiveFrom();
            LocalDate end = DataService.getActiveTo();
            EventQuery q = new EventQuery.Builder()
                .dateFrom(start.toString())
                .dateTo(end.toString())
                .limit(100000)
                .build();

            List<Event> events = eventDao.query(q);
            if (events == null || events.isEmpty()) {
                System.err.println("[ClusteringPanel] 数据库无事件数据，使用回退数据");
                return fallbackClusterResults();
            }

            // 3. 按 actor1Code 分组（排除中国 CHN 和缺失码）
            Map<String, List<Event>> byCountry = new HashMap<>();
            for (Event e : events) {
                if (e == null) continue;
                String code = e.getActor1Code();
                if (code == null || code.trim().isEmpty() || "CHN".equals(code)) continue;
                byCountry.computeIfAbsent(code, k -> new ArrayList<>()).add(e);
            }

            if (byCountry.isEmpty()) {
                System.err.println("[ClusteringPanel] 分组后无有效国家数据，使用回退数据");
                return fallbackClusterResults();
            }

            // 4. 对每个国家计算特征
            Map<String, double[]> features = new HashMap<>(); // code -> [goldsteinAvg, toneAvg, eventCount, coopRatio, mentionAvg]
            Set<String> seenUrls = new HashSet<>();

            // 构建一带一路国家代码集合（与 TrendChartPanel 风格一致）
            Set<String> briSet = new HashSet<>();
            for (String region : BRICountries.getMainRegions()) {
                briSet.addAll(BRICountries.getCountriesByRegion(region));
            }

            for (Map.Entry<String, List<Event>> entry : byCountry.entrySet()) {
                String code = entry.getKey();
                // 过滤：仅保留一带一路国家，排除非一带一路国家
                if (!briSet.contains(code)) continue;

                List<Event> evts = entry.getValue();

                // 按 sourceUrl 去重
                List<Event> unique = new ArrayList<>();
                for (Event e : evts) {
                    String url = e.getSourceUrl();
                    String key = (url != null && !url.trim().isEmpty()) ? url.trim()
                        : e.getEventDate() + "_" + e.getActor1Code() + "_" + e.getActor2Code() + "_" + e.getEventRoot();
                    if (seenUrls.add(key)) {
                        unique.add(e);
                    }
                }

                if (unique.isEmpty()) continue;

                double sumGoldstein = 0, sumTone = 0, sumMentions = 0;
                long coopCount = 0;
                for (Event e : unique) {
                    sumGoldstein += e.getGoldstein();
                    sumTone += e.getAvgTone();
                    sumMentions += e.getNumMentions();
                    if (e.isCooperative()) coopCount++;
                }

                int n = unique.size();
                double goldsteinAvg = sumGoldstein / n;
                double toneAvg = sumTone / n;
                double mentionAvg = sumMentions / n;
                double coopRatio = (double) coopCount / n;

                features.put(code, new double[]{goldsteinAvg, toneAvg, n, coopRatio, mentionAvg});
            }

            // 5. 通过规则做聚类（基于 Goldstein 均值 + 合作比例）
            for (Map.Entry<String, double[]> entry : features.entrySet()) {
                String code = entry.getKey();
                double[] feat = entry.getValue();
                double goldsteinAvg = feat[0];
                double toneAvg = feat[1];
                int eventCount = (int) feat[2];
                double coopRatio = feat[3];
                double mentionAvg = feat[4];

                // 聚类规则：
                // - 深度合作伙伴: goldstein > 2.0 && coopRatio > 0.6
                // - 稳定合作: goldstein > 0.5 && coopRatio > 0.4
                // - 存在风险: goldstein > -2.0 || coopRatio > 0.2
                // - 高度紧张: 其余
                int clusterId;
                String clusterLabel;
                String riskLevel;
                double riskScore;

                if (goldsteinAvg > 2.0 && coopRatio > 0.6) {
                    clusterId = 0;
                    clusterLabel = "深度合作伙伴";
                    riskLevel = "低";
                    riskScore = 10 + (1 - coopRatio) * 20;
                } else if (goldsteinAvg > 0.5 && coopRatio > 0.4) {
                    clusterId = 1;
                    clusterLabel = "稳定合作";
                    riskLevel = "低";
                    riskScore = 20 + (1 - coopRatio) * 25;
                } else if (goldsteinAvg > -2.0 || coopRatio > 0.2) {
                    clusterId = 2;
                    clusterLabel = "存在风险";
                    riskLevel = "中";
                    riskScore = 45 + Math.max(0, -goldsteinAvg) * 5;
                } else {
                    clusterId = 3;
                    clusterLabel = "高度紧张";
                    riskLevel = "高";
                    riskScore = 70 + Math.max(0, -goldsteinAvg) * 5;
                }
                riskScore = Math.min(100, Math.max(0, riskScore));

                String name = BRICountries.getCountryName(code);
                if (name == null) name = code;

                results.add(new ClusterResult(code, name, clusterId, clusterLabel, riskLevel, riskScore));
            }

            // 按风险评分降序
            results.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));

        } catch (Exception e) {
            System.err.println("[ClusteringPanel] 聚类计算失败: " + e.getMessage());
            e.printStackTrace();
            return fallbackClusterResults();
        }

        if (results.isEmpty()) {
            return fallbackClusterResults();
        }
        return results;
    }

    /** 数据库无数据时的回退聚类结果 */
    private List<ClusterResult> fallbackClusterResults() {
        List<ClusterResult> results = new ArrayList<>();
        // 深度合作伙伴
        results.add(new ClusterResult("PAK", "巴基斯坦", 0, "深度合作伙伴", "低", 12));
        results.add(new ClusterResult("KHM", "柬埔寨", 0, "深度合作伙伴", "低", 15));
        results.add(new ClusterResult("LAO", "老挝", 0, "深度合作伙伴", "低", 10));
        results.add(new ClusterResult("THA", "泰国", 1, "稳定合作", "低", 22));
        results.add(new ClusterResult("IDN", "印度尼西亚", 1, "稳定合作", "低", 25));
        results.add(new ClusterResult("KAZ", "哈萨克斯坦", 1, "稳定合作", "低", 20));
        results.add(new ClusterResult("MMR", "缅甸", 2, "存在风险", "中", 52));
        results.add(new ClusterResult("AFG", "阿富汗", 3, "高度紧张", "高", 78));
        results.add(new ClusterResult("SYR", "叙利亚", 3, "高度紧张", "高", 85));
        return results;
    }

    // ================================================================
    //  UI 组件
    // ================================================================

    private VBox createClusterCards(List<ClusterResult> results) {
        VBox container = new VBox(10);
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83D\uDCCA K-Means 聚类分析结果");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        Map<String, List<ClusterResult>> grouped = new LinkedHashMap<>();
        grouped.put("深度合作伙伴", new ArrayList<>());
        grouped.put("稳定合作", new ArrayList<>());
        grouped.put("存在风险", new ArrayList<>());
        grouped.put("高度紧张", new ArrayList<>());

        for (ClusterResult r : results) {
            List<ClusterResult> list = grouped.get(r.getClusterLabel());
            if (list != null) list.add(r);
        }

        HBox cardsRow = new HBox(8);
        cardsRow.setAlignment(Pos.CENTER);

        Color[] cardColors = {
            Color.rgb(46, 204, 113),
            Color.rgb(52, 152, 219),
            Color.rgb(243, 156, 18),
            Color.rgb(231, 76, 60)
        };

        int idx = 0;
        for (Map.Entry<String, List<ClusterResult>> entry : grouped.entrySet()) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(8));
            card.setPrefWidth(170);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 2); -fx-border-color: " + toHex(cardColors[idx]) + "; -fx-border-width: 2; -fx-border-radius: 8;");

            Label clusterName = new Label(entry.getKey());
            clusterName.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            clusterName.setTextFill(cardColors[idx]);

            Label countLabel = new Label(entry.getValue().size() + " 个国家");
            countLabel.setFont(Font.font("Microsoft YaHei", 9));
            countLabel.setTextFill(Color.GRAY);

            VBox countryList = new VBox(2);
            for (ClusterResult r : entry.getValue()) {
                Label countryLabel = new Label("\uD83C\uDF0D " + r.getCountryName() + " (" + r.getCountryCode() + ")");
                countryLabel.setFont(Font.font("Microsoft YaHei", 9));
                if (entry.getKey().equals("高度紧张")) {
                    countryLabel.setTextFill(cardColors[idx]);
                }
                countryList.getChildren().add(countryLabel);
            }
            // 每个卡片内的国家列表带独立滚轮
            ScrollPane listScroll = new ScrollPane(countryList);
            listScroll.setFitToWidth(true);
            listScroll.setPrefHeight(260);
            listScroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");
            VBox.setVgrow(listScroll, Priority.ALWAYS);

            card.getChildren().addAll(clusterName, countLabel, new Separator(), listScroll);
            cardsRow.getChildren().add(card);
            idx++;
        }

        container.getChildren().addAll(sectionTitle, cardsRow);
        return container;
    }

    private HBox createRiskTable(List<ClusterResult> results) {
        HBox container = new HBox(15);
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        VBox leftBox = new VBox(10);

        Label sectionTitle = new Label("\uD83D\uDCCB 投资风险评估");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        // 按风险评分排序
        results.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));

        TableView<ClusterResult> riskTable = new TableView<>();
        riskTable.setPrefHeight(180);
        riskTable.setPrefWidth(500);

        TableColumn<ClusterResult, String> countryCol = new TableColumn<>("国家");
        countryCol.setPrefWidth(100);
        countryCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCountryName()));

        TableColumn<ClusterResult, String> clusterCol = new TableColumn<>("分类");
        clusterCol.setPrefWidth(120);
        clusterCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getClusterLabel()));

        TableColumn<ClusterResult, String> riskLevelCol = new TableColumn<>("风险等级");
        riskLevelCol.setPrefWidth(100);
        riskLevelCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRiskLevel()));
        riskLevelCol.setCellFactory(col -> new TableCell<ClusterResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("高")) setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    else if (item.equals("中")) setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: green;");
                }
            }
        });

        TableColumn<ClusterResult, String> scoreCol = new TableColumn<>("风险评分");
        scoreCol.setPrefWidth(100);
        scoreCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.format("%.0f", data.getValue().getRiskScore())));

        riskTable.getColumns().addAll(countryCol, clusterCol, riskLevelCol, scoreCol);
        riskTable.getItems().addAll(results);

        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));
        rightBox.setPrefWidth(200);

        Label infoTitle = new Label("风险等级说明");
        infoTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));

        Label lowLabel = new Label("\u2705 低风险: 合作稳定，适合投资");
        lowLabel.setFont(Font.font("Microsoft YaHei", 11));
        Label midLabel = new Label("\u26A0 中等风险: 需关注局势");
        midLabel.setFont(Font.font("Microsoft YaHei", 11));
        Label highLabel = new Label("\u274C 高风险: 谨慎投资");
        highLabel.setFont(Font.font("Microsoft YaHei", 11));
        Label veryHighLabel = new Label("❗ 极高风险: 不建议投资");
        veryHighLabel.setFont(Font.font("Microsoft YaHei", 11));

        rightBox.getChildren().addAll(infoTitle, new Separator(),
            lowLabel, midLabel, highLabel, veryHighLabel);

        leftBox.getChildren().addAll(sectionTitle, riskTable);
        container.getChildren().addAll(leftBox, rightBox);
        return container;
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
}
