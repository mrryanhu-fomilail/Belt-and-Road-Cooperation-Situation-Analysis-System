package com.yourteam.bri.gui;

import com.bri.analysis.CountryInfoProvider;
import com.bri.engine.BriAnalysisEngine;
import com.bri.model.Event;
import com.bri.model.Country;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventQuery;
import com.yourteam.bri.model.NewsEvent;
import com.yourteam.bri.service.BriEngineHolder;
import com.yourteam.bri.util.BRICountries;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 新闻面板 - 基于已导入 GDELT 数据库中的真实事件数据
 */
public class NewsPanel extends VBox {

    private List<NewsEvent> allNews = new ArrayList<>();
    private List<NewsEvent> filteredNews = new ArrayList<>();

    private VBox newsListContainer;
    private ComboBox<String> sentimentFilter;
    private ComboBox<String> countryFilter;
    private TextField searchField;
    private Label pageLabel;
    private TextFlow statsFlow;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 8;


private static final Map<String, String> ROOT_LABELS = new HashMap<>();
    /** 单边事件标签（仅一方有 actor，不含"合作""冲突"等双边含义） */
    private static final Map<String, String> UNILATERAL_LABELS = new HashMap<>();
    static {
        ROOT_LABELS.put("01", "发表声明");
        ROOT_LABELS.put("02", "呼吁行动");
        ROOT_LABELS.put("03", "外交合作");
        ROOT_LABELS.put("04", "经济合作");
        ROOT_LABELS.put("05", "军事合作");
        ROOT_LABELS.put("06", "援助合作");
        ROOT_LABELS.put("07", "国际法合作");
        ROOT_LABELS.put("08", "威胁行为");
        ROOT_LABELS.put("09", "抗议行为");
        ROOT_LABELS.put("10", "军事冲突");
        ROOT_LABELS.put("11", "制裁与抵制");
        ROOT_LABELS.put("12", "外交冲突");
        ROOT_LABELS.put("13", "经济冲突");
        ROOT_LABELS.put("14", "暴力冲突");
        ROOT_LABELS.put("15", "军事行动");

        // 单边事件对应标签
        UNILATERAL_LABELS.put("01", "发表声明");
        UNILATERAL_LABELS.put("02", "呼吁行动");
        UNILATERAL_LABELS.put("03", "外交活动");
        UNILATERAL_LABELS.put("04", "经济活动");
        UNILATERAL_LABELS.put("05", "军事活动");
        UNILATERAL_LABELS.put("06", "援助活动");
        UNILATERAL_LABELS.put("07", "国际法活动");
        UNILATERAL_LABELS.put("08", "威胁行为");
        UNILATERAL_LABELS.put("09", "抗议行为");
        UNILATERAL_LABELS.put("10", "军事行动");
        UNILATERAL_LABELS.put("11", "制裁措施");
        UNILATERAL_LABELS.put("12", "外交抗议");
        UNILATERAL_LABELS.put("13", "经济措施");
        UNILATERAL_LABELS.put("14", "暴力活动");
        UNILATERAL_LABELS.put("15", "军事行动");
    }

    public NewsPanel() {
        setPrefWidth(320);
        setMinWidth(300);
        setPadding(new Insets(12));
        setSpacing(8);
        setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);"
        );

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("\uD83D\uDCF0 \u65B0\u95FB\u52A8\u6001");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("\uD83D\uDD04");
        refreshBtn.setStyle("-fx-font-size: 14px; -fx-background-color: transparent; -fx-cursor: hand;");
        refreshBtn.setTooltip(new Tooltip("刷新新闻"));
        refreshBtn.setOnAction(e -> refreshNews());

        headerRow.getChildren().addAll(titleLabel, spacer, refreshBtn);

        statsFlow = new TextFlow();
        statsFlow.setPrefHeight(20);
        Text loadingText = new Text("加载中...");
        loadingText.setFont(Font.font("Microsoft YaHei", 10));
        loadingText.setStyle("-fx-fill: #808080;");
        statsFlow.getChildren().add(loadingText);

        HBox filterRow = new HBox(6);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        sentimentFilter = new ComboBox<>();
        sentimentFilter.getItems().addAll("全部", "正面", "中性", "负面");
        sentimentFilter.setValue("全部");
        sentimentFilter.setStyle("-fx-font-size: 11px; -fx-pref-width: 80px;");
        sentimentFilter.setOnAction(e -> filterNews());

        countryFilter = new ComboBox<>();
        // 全部第一，中国第二，其余按代码排序
        List<String> countryItems = new ArrayList<>();
        countryItems.add("全部");
        countryItems.add("中国 (CHN)");
        List<String> sortedCodes = new ArrayList<>(BRICountries.getCountryCodes());
        Collections.sort(sortedCodes);
        for (String code : sortedCodes) {
            if (!code.equals("CHN")) {
                countryItems.add(BRICountries.getCountryName(code) + " (" + code + ")");
            }
        }
        countryFilter.getItems().addAll(countryItems);
        countryFilter.setValue("全部");
        countryFilter.setStyle("-fx-font-size: 11px; -fx-pref-width: 120px;");
        countryFilter.setOnAction(e -> filterNews());

        searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D \u641C\u7D22\u65B0\u95FB...");
        searchField.setStyle("-fx-font-size: 11px; -fx-pref-width: 80px;");
        searchField.textProperty().addListener((obs, old, val) -> filterNews());

        filterRow.getChildren().addAll(sentimentFilter, countryFilter, searchField);

        newsListContainer = new VBox(6);
        newsListContainer.setPadding(new Insets(4, 0, 4, 0));

        ScrollPane scrollPane = new ScrollPane(newsListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox pageRow = new HBox(8);
        pageRow.setAlignment(Pos.CENTER);

        Button prevBtn = new Button("<");
        prevBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #f0f0f0; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        prevBtn.setOnAction(e -> {
            if (currentPage > 1) { currentPage--; renderNewsPage(); }
        });

        pageLabel = new Label("\u7B2C 0 \u9875");
        pageLabel.setFont(Font.font("Microsoft YaHei", 11));
        pageLabel.setStyle("-fx-text-fill: #808080;");

        Button nextBtn = new Button(">");
        nextBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #f0f0f0; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        nextBtn.setOnAction(e -> {
            int maxPage = (int) Math.ceil((double) filteredNews.size() / PAGE_SIZE);
            if (currentPage < maxPage) { currentPage++; renderNewsPage(); }
        });

        pageRow.getChildren().addAll(prevBtn, pageLabel, nextBtn);

        getChildren().addAll(headerRow, statsFlow, filterRow, scrollPane, pageRow);

        loadNewsFromDatabase();
    }

    private void loadNewsFromDatabase() {
        allNews.clear();
        try {
            BriAnalysisEngine engine = BriEngineHolder.getEngine();
            List<Event> events = loadEventsFromDb();
            int id = 1;
            // 按 event_date + source_url 去重（同一天同一来源只算一条）
            Set<String> seen = new HashSet<>();
            for (Event e : events) {
                String dedupKey = e.getEventDate() + "|" + (e.getSourceUrl() != null ? e.getSourceUrl().trim() : "");
                if (!seen.add(dedupKey)) continue;

                if (id > 500) break;

                NewsEvent news = new NewsEvent();
                news.setNewsId(id++);
                news.setNewsDate(parseDate(e.getEventDate()));

                String code = e.getActor2Code();
                if (code == null || code.isEmpty() || "CHN".equals(code)) {
                    code = e.getActor1Code();
                }
                if ("CHN".equals(code) && e.getActor2Code() != null && !e.getActor2Code().isEmpty()) {
                    code = e.getActor2Code();
                }
                news.setCountryCode(code != null ? code : "UNK");
                news.setCountryName(getNameCn(code));
                news.setCityName("");

                double tone = e.getAvgTone();
                news.setSentiment(tone);
                if (tone > 2.0) {
                    news.setSentimentLabel("正面");
                } else if (tone < -2.0) {
                    news.setSentimentLabel("负面");
                } else {
                    news.setSentimentLabel("中性");
                }

                news.setGoldsteinScale(e.getGoldstein());
                news.setNumMentions(e.getNumMentions());

                String a1Name = getNameCn(e.getActor1Code());
                String a2Name = getNameCn(e.getActor2Code());
                boolean a1Known = !"未知".equals(a1Name);
                boolean a2Known = !"未知".equals(a2Name);

                String title;
                String summary;
                if (a1Known && a2Known) {
                    String label = ROOT_LABELS.getOrDefault(e.getEventRoot(),
                            ROOT_LABELS.getOrDefault("01", "一般事件"));
                    title = String.format("%s与%s的%s", a1Name, a2Name, label);
                    summary = String.format("%s与%s发生%s类事件(Goldstein=%.1f, Tone=%.1f)",
                        a1Name, a2Name, e.getEventCode(), e.getGoldstein(), e.getAvgTone());
                } else if (a1Known) {
                    String label = UNILATERAL_LABELS.getOrDefault(e.getEventRoot(),
                            UNILATERAL_LABELS.getOrDefault("01", "一般事件"));
                    title = String.format("%s%s", a1Name, label);
                    summary = String.format("%s%s(Goldstein=%.1f, Tone=%.1f)",
                        a1Name, label, e.getGoldstein(), e.getAvgTone());
                } else if (a2Known) {
                    String label = UNILATERAL_LABELS.getOrDefault(e.getEventRoot(),
                            UNILATERAL_LABELS.getOrDefault("01", "一般事件"));
                    title = String.format("%s%s", a2Name, label);
                    summary = String.format("%s%s(Goldstein=%.1f, Tone=%.1f)",
                        a2Name, label, e.getGoldstein(), e.getAvgTone());
                } else {
                    title = "未命名事件";
                    summary = String.format("事件代码%s(Goldstein=%.1f, Tone=%.1f)",
                        e.getEventCode(), e.getGoldstein(), e.getAvgTone());
                }
                news.setSummary(summary);
                news.setTitle(title);
                news.setSourceUrl(e.getSourceUrl());

                allNews.add(news);
            }  // 结束 for 循环
        } catch (Exception ex) {
            Text noDataText = new Text("暂无事件数据");
            noDataText.setFont(Font.font("Microsoft YaHei", 10));
            noDataText.setStyle("-fx-fill: #808080;");
            statsFlow.getChildren().setAll(noDataText);
        }

        allNews.sort((a, b) -> b.getNewsDate().compareTo(a.getNewsDate()));
        filterNews();
    }

    private List<Event> loadEventsFromDb() {
        try {
            DaoFactory df;
            try {
                df = DaoFactory.getInstance();
            } catch (IllegalStateException e) {
                df = DaoFactory.getInstance("bri.db");
            }
            // 取数据库中最大日期（最新数据），只返回最近一天的数据
            LocalDate maxDate = com.yourteam.bri.service.DataService.getActiveTo();
            EventQuery q = new EventQuery.Builder()
                .dateFrom(maxDate.toString())
                .dateTo(maxDate.toString())
                .limit(200)
                .build();
            return df.eventDao().query(q);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDate.now();
        try {
            if (dateStr.length() == 8) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            return LocalDate.parse(dateStr.substring(0, 10));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private String getNameCn(String code) {
        if (code == null || code.isEmpty() || "UNK".equals(code)) return "未知";
        // 1. 优先从引擎数据库查找（已导入的 BRI 国家）
        try {
            Country c = BriEngineHolder.getEngine().getCountry(code);
            if (c != null && c.getNameCn() != null) return c.getNameCn();
        } catch (Exception ignored) {}
        // 2. 从 CountryInfoProvider 硬编码列表查找（99个常见国家）
        String cn = CountryInfoProvider.getNameCn(code);
        if (cn != null && !cn.equals(code)) return cn;
        // 3. 从 BRICountries 查找（102个 BRI 国家）
        String brCN = BRICountries.getCountryName(code);
        if (brCN != null && !brCN.equals(code)) return brCN;
        // 4. 最终回退：显示代码
        return code;
    }

    private void filterNews() {
        String sentiment = sentimentFilter.getValue();
        String countryText = countryFilter.getValue();
        String keyword = searchField.getText().toLowerCase();

        filteredNews = allNews.stream()
            .filter(n -> {
                if (sentiment != null && !sentiment.equals("全部")) {
                    return sentiment.equals(n.getSentimentLabel());
                }
                return true;
            })
            .filter(n -> {
                if (countryText != null && !countryText.equals("全部")) {
                    return countryText.contains(n.getCountryCode());
                }
                return true;
            })
            .filter(n -> {
                if (keyword != null && !keyword.isEmpty()) {
                    return n.getTitle().toLowerCase().contains(keyword) ||
                           n.getCountryName().toLowerCase().contains(keyword);
                }
                return true;
            })
            .collect(Collectors.toList());

        currentPage = 1;
        long pos = filteredNews.stream().filter(n -> "正面".equals(n.getSentimentLabel())).count();
        long neg = filteredNews.stream().filter(n -> "负面".equals(n.getSentimentLabel())).count();
        long neu = filteredNews.stream().filter(n -> "中性".equals(n.getSentimentLabel())).count();

        // 使用 TextFlow 实现不同颜色的 emoji 统计显示
        Text countText = new Text(String.format("共 %d 条  ", filteredNews.size()));
        countText.setFont(Font.font("Microsoft YaHei", 10));
        countText.setStyle("-fx-fill: #333333;");

        Text posText = new Text(String.format("\uD83D\uDC4D %d  ", pos));
        posText.setFont(Font.font("Microsoft YaHei", 10));
        posText.setStyle("-fx-fill: #4CAF50;");

        Text neuText = new Text(String.format("\uD83D\uDE10 %d  ", neu));
        neuText.setFont(Font.font("Microsoft YaHei", 10));
        neuText.setStyle("-fx-fill: #FF9800;");

        Text negText = new Text(String.format("\uD83D\uDC4E %d", neg));
        negText.setFont(Font.font("Microsoft YaHei", 10));
        negText.setStyle("-fx-fill: #F44336;");

        statsFlow.getChildren().setAll(countText, posText, neuText, negText);

        renderNewsPage();
    }

    private void renderNewsPage() {
        newsListContainer.getChildren().clear();

        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, filteredNews.size());

        if (fromIndex >= filteredNews.size()) {
            Label emptyLabel = new Label("暂无匹配新闻");
            emptyLabel.setFont(Font.font("Microsoft YaHei", 12));
            emptyLabel.setStyle("-fx-text-fill: #808080;");
            emptyLabel.setPadding(new Insets(20));
            newsListContainer.getChildren().add(emptyLabel);
            pageLabel.setText("第 0 页");
            return;
        }

        for (NewsEvent news : filteredNews.subList(fromIndex, toIndex)) {
            newsListContainer.getChildren().add(createNewsCard(news));
        }

        int totalPages = (int) Math.ceil((double) filteredNews.size() / PAGE_SIZE);
        pageLabel.setText("第 " + currentPage + "/" + totalPages + " 页");
    }

    private VBox createNewsCard(NewsEvent news) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 10, 8, 10));
        card.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-background-radius: 6;" +
            "-fx-border-color: #e8e8e8;" +
            "-fx-border-radius: 6;" +
            "-fx-border-width: 1;"
        );

        card.setOnMouseEntered(e ->
            card.setStyle("-fx-background-color: #f0f4ff; -fx-background-radius: 6; -fx-border-color: #c0d0ff; -fx-border-radius: 6; -fx-border-width: 1; -fx-cursor: hand;")
        );
        card.setOnMouseExited(e ->
            card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; -fx-border-color: #e8e8e8; -fx-border-radius: 6; -fx-border-width: 1;")
        );
        card.setOnMouseClicked(e -> showNewsDetail(news));

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        String icon;
        String iconColor;
        String label = news.getSentimentLabel();
        if ("正面".equals(label)) {
            icon = "\uD83D\uDC4D";
            iconColor = "#4CAF50";
        } else if ("负面".equals(label)) {
            icon = "\uD83D\uDC4E";
            iconColor = "#F44336";
        } else {
            icon = "\uD83D\uDE10";
            iconColor = "#FF9800";
        }

        Label iconLabel = new Label(icon);
        iconLabel.setFont(new Font(18));
        iconLabel.setStyle("-fx-text-fill: " + iconColor + ";");
        iconLabel.setMinWidth(20);

        Label titleLabel = new Label(news.getTitle());
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.MEDIUM, 12));
        titleLabel.setStyle("-fx-text-fill: #1e1e3c; -fx-wrap-text: true;");
        titleLabel.setMaxWidth(220);

        titleRow.getChildren().addAll(iconLabel, titleLabel);

        HBox infoRow = new HBox(8);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        Label countryLabel = new Label("\uD83C\uDF0D " + news.getCountryName());
        countryLabel.setFont(Font.font("Microsoft YaHei", 10));
        countryLabel.setStyle("-fx-text-fill: #64748b;");

        Label dateLabel = new Label("\uD83D\uDCC5 " + news.getNewsDate().toString());
        dateLabel.setFont(Font.font("Microsoft YaHei", 10));
        dateLabel.setStyle("-fx-text-fill: #64748b;");

        double sentiment = news.getSentiment();
        String sentimentText = String.format("%+.1f", sentiment);
        Label sentimentLabel = new Label(sentimentText);
        sentimentLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        if ("正面".equals(label)) sentimentLabel.setStyle("-fx-text-fill: #4CAF50;");
        else if ("负面".equals(label)) sentimentLabel.setStyle("-fx-text-fill: #F44336;");
        else sentimentLabel.setStyle("-fx-text-fill: #FF9800;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mentionsLabel = new Label("\uD83D\uDD14 " + news.getNumMentions());
        mentionsLabel.setFont(Font.font("Microsoft YaHei", 9));
        mentionsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-background-color: #f0f0f5; -fx-padding: 2 6; -fx-background-radius: 8;");

        infoRow.getChildren().addAll(countryLabel, dateLabel, sentimentLabel, spacer, mentionsLabel);

        card.getChildren().addAll(titleRow, infoRow);
        return card;
    }

    private void showNewsDetail(NewsEvent news) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("\u65B0\u95FB\u8BE6\u60C5 - " + news.getTitle());
        dialog.setHeaderText(null);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setPrefWidth(450);

        Label titleLabel = new Label(news.getTitle());
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #1e1e3c;");

        String iconColorHex;
        String icon;
        if ("正面".equals(news.getSentimentLabel())) {
            icon = "\uD83D\uDC4D 正面";
            iconColorHex = "#4CAF50";
        } else if ("负面".equals(news.getSentimentLabel())) {
            icon = "\uD83D\uDC4E 负面";
            iconColorHex = "#F44336";
        } else {
            icon = "\uD83D\uDE10 中性";
            iconColorHex = "#FF9800";
        }
        Label sentimentLabel = new Label("情感倾向: " + icon + " (" + String.format("%.2f", news.getSentiment()) + ")");
        sentimentLabel.setFont(Font.font("Microsoft YaHei", 12));
        sentimentLabel.setStyle("-fx-text-fill: " + iconColorHex + ";");

        Label infoLabel = new Label(
            "\u56FD\u5BB6: " + news.getCountryName() + " (" + news.getCountryCode() + ")\n" +
            "\u65E5\u671F: " + news.getNewsDate() + "\n" +
            "Goldstein\u6307\u6570: " + String.format("%.2f", news.getGoldsteinScale()) + "\n" +
            "\u5F15\u7528\u6B21\u6570: " + news.getNumMentions()
        );
        infoLabel.setFont(Font.font("Microsoft YaHei", 11));
        infoLabel.setStyle("-fx-text-fill: #64748b;");

        Label summaryLabel = new Label("\u6458\u8981: " + news.getSummary());
        summaryLabel.setFont(Font.font("Microsoft YaHei", 11));
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-text-fill: #333333;");

        Label detailLabel = new Label(
            "\u3010GDELT \u4E8B\u4EF6\u8BE6\u60C5\u3011\n\n" +
            "\u4E8B\u4EF6\u7F16\u53F7: #" + news.getNewsId() + "\n\n" +
            "\u8BE5\u4E8B\u4EF6\u6765\u6E90\u4E8E GDELT 2.0 \u6570\u636E\u5E93\uFF0C\u8BB0\u5F55\u4E86 " + news.getCountryName() +
            " \u4E0E\u4E00\u5E26\u4E00\u8DEF\u6CBF\u7EBF\u56FD\u5BB6\u7684\u4EA4\u4E92\u60C5\u51B5\u3002\n\n" +
            "Goldstein \u6307\u6570: " + String.format("%.2f", news.getGoldsteinScale()) +
            "\uFF08\u6B63\u6570\u8868\u793A\u5408\u4F5C\u503E\u5411\uFF0C\u8D1F\u6570\u8868\u793A\u51B2\u7A81\u503E\u5411\uFF09\n\n" +
            "\u60C5\u611F\u8BED\u8C03: " + String.format("%.2f", news.getSentiment()) +
            "\uFF08\u6570\u503C\u8D8A\u9AD8\u8D8A\u6B63\u9762\uFF09"
        );
        detailLabel.setFont(Font.font("Microsoft YaHei", 11));
        detailLabel.setWrapText(true);
        detailLabel.setStyle("-fx-text-fill: #505050;");

        // 超链接（如果有 sourceUrl）
        Hyperlink sourceLink = new Hyperlink();
        String url = news.getSourceUrl();
        if (url != null && !url.trim().isEmpty()) {
            sourceLink.setText("原文链接: " + url);
            sourceLink.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                    System.err.println("[NewsPanel] 无法打开链接: " + ex.getMessage());
                }
            });
        } else {
            sourceLink.setText("暂无原文链接");
            sourceLink.setDisable(true);
        }
        sourceLink.setFont(Font.font("Microsoft YaHei", 11));

        content.getChildren().addAll(titleLabel, new Separator(), sentimentLabel, infoLabel, new Separator(), summaryLabel, detailLabel, new Separator(), sourceLink);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

    public void refreshNews() {
        loadNewsFromDatabase();
    }

    public void setCountryFilter(String countryCode) {
        if (countryCode != null && !countryCode.isEmpty()) {
            String name = BRICountries.getCountryName(countryCode);
            countryFilter.setValue(name + " (" + countryCode + ")");
            filterNews();
        }
    }
}
