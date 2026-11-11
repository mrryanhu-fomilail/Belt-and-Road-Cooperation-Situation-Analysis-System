package com.yourteam.bri.gui;

import com.yourteam.bri.model.GDELTEvent;
import com.yourteam.bri.util.BRICountries;

import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.dao.EventQuery;
import com.bri.model.Event;
import com.bri.config.DataSource;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件查询检索面板 - 多条件组合查询 GDELT 事件数据（对应需求 F-06~F-11）
 * <p>
 * 提供多维度的事件检索功能，查询条件包括：
 * <ul>
 *   <li>参与国1（默认中国，支持任意国家选择）</li>
 *   <li>参与国2（可选，支持"全部"不限制）</li>
 *   <li>子区域（按地理区域筛选参与国）</li>
 *   <li>事件大类（全部/合作事件/冲突事件）</li>
 *   <li>事件子类（按 CAMEO 编码细分，如经济合作、外交合作、制裁、军事冲突等）</li>
 *   <li>起止日期（DatePicker 选择）</li>
 * </ul>
 * </p>
 * <p>
 * 查询结果通过下方分栏展示：
 * <ul>
 *   <li>左侧表格：事件日期、参与国、事件编码、子类型、Goldstein 值、情感语调</li>
 *   <li>右侧饼图：按事件子类统计的分布比例</li>
 * </ul>
 * </p>
 *
 * @see com.yourteam.bri.service.DataService#queryEvents 数据查询入口
 * @see com.yourteam.bri.model.GDELTEvent CAMEO 数据模型
 * @see ChartUtils#drawPieChart 结果分布图的绘制
 */
public class QueryPanel extends ScrollPane {

    private ComboBox<String> country1Combo;
    private ComboBox<String> country2Combo;
    private ComboBox<String> regionCombo;
    private ComboBox<String> eventTypeCombo;
    private ComboBox<String> subEventTypeCombo;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private TableView<Map<String, String>> resultTable;
    private Canvas pieCanvas;
    private Label resultCountLabel;

    /** 饼图标题标签（动态更新） */
    private Label pieTitleLabel;

    // 分页相关
    private List<GDELTEvent> allQueryResults;
    private int currentPage;
    private static final int PAGE_SIZE = 50;
    private Label pageInfoLabel;
    private Button prevPageBtn;
    private Button nextPageBtn;

    // CAMEO 编码映射：合作事件子类（Root Code 01-07 + 16-20）
    private static final Map<String, String> COOPERATION_SUB_TYPES = new LinkedHashMap<>();
    static {
        COOPERATION_SUB_TYPES.put("全部", "全部");
        COOPERATION_SUB_TYPES.put("发表声明 (01)", "01");
        COOPERATION_SUB_TYPES.put("呼吁 (02)", "02");
        COOPERATION_SUB_TYPES.put("合作表达 (03)", "03");
        COOPERATION_SUB_TYPES.put("外交合作 (04)", "04");
        COOPERATION_SUB_TYPES.put("经济合作 (05)", "05");
        COOPERATION_SUB_TYPES.put("援助 (06)", "06");
        COOPERATION_SUB_TYPES.put("其他合作 (07)", "07");
        COOPERATION_SUB_TYPES.put("提供援助 (16)", "16");
        COOPERATION_SUB_TYPES.put("经济援助 (17)", "17");
        COOPERATION_SUB_TYPES.put("人道援助 (18)", "18");
        COOPERATION_SUB_TYPES.put("军事合作 (19)", "19");
        COOPERATION_SUB_TYPES.put("其他积极行为 (20)", "20");
    }

    // CAMEO 编码映射：冲突事件子类（Root Code 08-14 + 21-22）
    private static final Map<String, String> CONFLICT_SUB_TYPES = new LinkedHashMap<>();
    static {
        CONFLICT_SUB_TYPES.put("全部", "全部");
        CONFLICT_SUB_TYPES.put("威胁 (08)", "08");
        CONFLICT_SUB_TYPES.put("抗议 (09)", "09");
        CONFLICT_SUB_TYPES.put("展示军力 (10)", "10");
        CONFLICT_SUB_TYPES.put("制裁 (11)", "11");
        CONFLICT_SUB_TYPES.put("军事冲突 (12)", "12");
        CONFLICT_SUB_TYPES.put("战争 (13)", "13");
        CONFLICT_SUB_TYPES.put("侵犯 (14)", "14");
        CONFLICT_SUB_TYPES.put("其他冲突行为 (21)", "21");
        CONFLICT_SUB_TYPES.put("暴力行为 (22)", "22");
    }

    // CAMEO 编码映射：竞争事件子类（Root Code 15）
    private static final Map<String, String> COMPETITION_SUB_TYPES = new LinkedHashMap<>();
    static {
        COMPETITION_SUB_TYPES.put("全部", "全部");
        COMPETITION_SUB_TYPES.put("非常规暴力行为 (15)", "15");
    }

    public QueryPanel() {
        setFitToWidth(true);
        setStyle("-fx-background: #f5f7fa; -fx-background-color: transparent;");

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f5f7fa;");

        // 标题
        Label titleLabel = new Label("\uD83D\uDD0D 事件查询检索");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        // 查询条件区域
        VBox queryBox = createQueryBox();

        // 结果显示区域
        VBox resultBox = createResultBox();

        content.getChildren().addAll(titleLabel, queryBox, resultBox);
        setContent(content);
    }

    /**
     * 创建查询条件输入区域
     * <p>
     * 包含三行控件：
     * <ul>
     *   <li>第一行：参与国1 + 参与国2下拉选择框（支持模糊搜索和手动输入）</li>
     *   <li>第二行：子区域 + 事件大类 + 事件子类</li>
     *   <li>第三行：开始日期 + 结束日期 + 查询按钮 + 清空按钮 + 结果计数标签</li>
     * </ul>
     * 国家列表从 BRICountries 获取，自动按名称排序。
     * </p>
     *
     * @return 查询条件面板 VBox
     */
    @SuppressWarnings("unchecked")
    private VBox createQueryBox() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label sectionTitle = new Label("\uD83D\uDD0D 查询条件");
        sectionTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        // 第一行：参与国
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label country1Label = new Label("参与国1:");
        country1Label.setFont(Font.font("Microsoft YaHei", 12));
        country1Combo = new ComboBox<>();
        country1Combo.setPrefWidth(180);

        Label country2Label = new Label("参与国2:");
        country2Label.setFont(Font.font("Microsoft YaHei", 12));
        country2Combo = new ComboBox<>();
        country2Combo.setPrefWidth(180);

        // 构建国家候选列表（按英文代码排序，首位"全部"，默认中国第二）
        List<String> country1Items = new ArrayList<>();
        country1Items.add("全部");
        country1Items.add("中国 (CHN)");
        List<BRICountries.CountryInfo> allInfos = new ArrayList<>(BRICountries.getAllCountries());
        allInfos.sort(Comparator.comparing(BRICountries.CountryInfo::getCode));
        for (BRICountries.CountryInfo info : allInfos) {
            if (!"CHN".equals(info.getCode())) {
                country1Items.add(info.getName() + " (" + info.getCode() + ")");
            }
        }
        country1Combo.getItems().addAll(country1Items);
        country1Combo.setValue("中国 (CHN)");
        // 设置自动补全（返回 StackPane 包装）
        StackPane country1Wrapper = setupAutoComplete(country1Combo, country1Items);

        List<String> country2Items = new ArrayList<>(country1Items);
        country2Combo.getItems().addAll(country2Items);
        country2Combo.setValue("全部");
        // 设置自动补全（返回 StackPane 包装）
        StackPane country2Wrapper = setupAutoComplete(country2Combo, country2Items);

        row1.getChildren().addAll(country1Label, country1Wrapper, country2Label, country2Wrapper);

        // 第二行：区域、事件大类、事件子类
        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label regionLabel = new Label("子区域:");
        regionLabel.setFont(Font.font("Microsoft YaHei", 12));
        regionCombo = new ComboBox<>();
        regionCombo.setPrefWidth(120);
        regionCombo.getItems().add("全部");
        regionCombo.getItems().addAll(
            "东亚", "东南亚", "中亚", "南亚", "中东",
            "欧洲", "非洲", "北美洲", "南美洲", "大洋洲"
        );
        regionCombo.getSelectionModel().select(0);

        Label typeLabel = new Label("事件大类:");
        typeLabel.setFont(Font.font("Microsoft YaHei", 12));
        eventTypeCombo = new ComboBox<>();
        eventTypeCombo.setPrefWidth(110);
        eventTypeCombo.getItems().addAll("全部", "合作事件 (01-07,16-20)", "冲突事件 (08-14,21-22)", "竞争事件 (15)");
        eventTypeCombo.getSelectionModel().select(0);
        // 大类变化时更新子类选项
        eventTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateSubEventTypes());

        Label subTypeLabel = new Label("子类型:");
        subTypeLabel.setFont(Font.font("Microsoft YaHei", 12));
        subEventTypeCombo = new ComboBox<>();
        subEventTypeCombo.setPrefWidth(130);
        subEventTypeCombo.getItems().add("全部");
        subEventTypeCombo.getSelectionModel().select(0);

        row2.getChildren().addAll(regionLabel, regionCombo, typeLabel, eventTypeCombo,
            subTypeLabel, subEventTypeCombo);

        // 第三行：日期 + 按钮
        HBox row3 = new HBox(15);
        row3.setAlignment(Pos.CENTER_LEFT);

        Label startLabel = new Label("开始日期:");
        startLabel.setFont(Font.font("Microsoft YaHei", 12));
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(1));
        startDatePicker.setPrefWidth(120);
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && endDatePicker.getValue() != null && newVal.isAfter(endDatePicker.getValue())) {
                endDatePicker.setValue(newVal);
            }
        });

        Label endLabel = new Label("结束日期:");
        endLabel.setFont(Font.font("Microsoft YaHei", 12));
        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefWidth(120);
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && startDatePicker.getValue() != null && newVal.isBefore(startDatePicker.getValue())) {
                startDatePicker.setValue(newVal);
            }
        });

        Button searchBtn = new Button("\uD83D\uDD0D 查询");
        searchBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8px 20px; -fx-background-radius: 5;");
        searchBtn.setOnAction(e -> performQuery());

        Button clearBtn = new Button("\uD83D\uDDD1 清空");
        clearBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 20px; -fx-background-radius: 5;");
        clearBtn.setOnAction(e -> clearQuery());

        resultCountLabel = new Label("查询结果: 0 条记录");
        resultCountLabel.setFont(Font.font("Microsoft YaHei", 12));
        resultCountLabel.setTextFill(Color.GRAY);

        row3.getChildren().addAll(startLabel, startDatePicker, endLabel, endDatePicker, searchBtn, clearBtn, resultCountLabel);

        box.getChildren().addAll(sectionTitle, row1, row2, row3);
        return box;
    }

    /**
     * 根据选中的事件大类，更新子类型下拉选项
     */
    private void updateSubEventTypes() {
        String selected = eventTypeCombo.getValue();
        subEventTypeCombo.getItems().clear();
        if (selected != null && selected.contains("合作事件")) {
            subEventTypeCombo.getItems().addAll(COOPERATION_SUB_TYPES.keySet());
        } else if (selected != null && selected.contains("冲突事件")) {
            subEventTypeCombo.getItems().addAll(CONFLICT_SUB_TYPES.keySet());
        } else if (selected != null && selected.contains("竞争事件")) {
            subEventTypeCombo.getItems().addAll(COMPETITION_SUB_TYPES.keySet());
        } else {
            subEventTypeCombo.getItems().add("全部");
        }
        subEventTypeCombo.getSelectionModel().select(0);
    }

    /**
     * 设置 ComboBox 的双模式功能：
     * <p>
     * 点击右侧下拉按钮（箭头）→ 只显示完整列表供选择（只读模式，不可输入）
     * 点击左侧文本框区域 → 可输入文字进行实时过滤，并弹出过滤后的下拉列表供选择
     * </p>
     * <p>
     * 实现原理：用 StackPane 叠放下层不可编辑的 ComboBox（响应箭头点击）
     * 和上层可编辑的 TextField（响应文字输入，右侧留空让箭头可穿透点击）。
     * </p>
     *
     * @param combo     ComboBox 控件
     * @param candidates 候选列表
     * @return 包装了 combo 和编辑器的 StackPane，调用方需用其替换原 combo 节点
     */
    private StackPane setupAutoComplete(ComboBox<String> combo, List<String> candidates) {
        combo.setEditable(false);
        combo.setPrefWidth(180);
        combo.setMaxHeight(30);

        // 记录当前选中的值
        final String[] selectedValue = {combo.getValue()};

        // 点击箭头时恢复完整候选列表（只读选择模式）
        combo.setOnShowing(event -> {
            combo.getItems().setAll(candidates);
        });

        // --- 叠加上层可编辑 TextField（留出右侧箭头区域） ---
        TextField editor = new TextField();
        editor.setText(combo.getValue() != null ? combo.getValue() : "");
        editor.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #d3d3d3; " +
            "-fx-border-width: 1; " +
            "-fx-padding: 4px 0 4px 7px; " +
            "-fx-font-size: 12px; " +
            "-fx-background-radius: 0;"
        );

        // 记录当前是否正在通过代码设置文本
        final boolean[] isSettingText = {false};

        // 编辑器文本变化时 → 实时过滤并弹出
        editor.textProperty().addListener((obs, oldText, newText) -> {
            if (isSettingText[0]) return;

            String input = newText.trim();

            if (input.isEmpty()) {
                if (!combo.getItems().equals(candidates)) {
                    combo.getItems().setAll(candidates);
                }
                if (!combo.isShowing()) {
                    combo.show();
                }
                return;
            }

            boolean isEnglish = input.matches("[a-zA-Z].*");
            String lowerInput = input.toLowerCase();
            List<String> filtered;
            if (isEnglish) {
                // 输入英文 → 匹配英文代码（忽略大小写，包含匹配）
                filtered = candidates.stream()
                        .filter(item -> {
                            int start = item.lastIndexOf('(');
                            int end = item.lastIndexOf(')');
                            if (start >= 0 && end > start) {
                                String codePart = item.substring(start + 1, end);
                                return codePart.toLowerCase().contains(lowerInput);
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
            } else {
                // 输入中文 → 匹配中文名称（忽略大小写，包含匹配）
                filtered = candidates.stream()
                        .filter(item -> {
                            int end = item.lastIndexOf('(');
                            String namePart = (end >= 0) ? item.substring(0, end).trim() : item;
                            // 排除"全部"项
                            if ("全部".equals(namePart)) return false;
                            return namePart.toLowerCase().contains(lowerInput);
                        })
                        .collect(Collectors.toList());
            }

            if (filtered.isEmpty()) {
                // 无匹配时展示"无"
                if (combo.getItems().size() != 1 || !"无".equals(combo.getItems().get(0))) {
                    combo.getItems().setAll(List.of("无"));
                }
            } else {
                combo.getItems().setAll(filtered);
            }

            if (!combo.isShowing()) {
                combo.show();
            }
        });

        // 从下拉列表选择后，同步更新 editor
        combo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && candidates.contains(newVal)) {
                selectedValue[0] = newVal;
                if (!newVal.equals(editor.getText())) {
                    isSettingText[0] = true;
                    editor.setText(newVal);
                    isSettingText[0] = false;
                }
            }
        });

        // 失焦时如果文本无效则恢复
        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String text = editor.getText().trim();
                if (!text.isEmpty() && !"无".equals(text) && !candidates.contains(text)) {
                    if (selectedValue[0] != null && candidates.contains(selectedValue[0])) {
                        isSettingText[0] = true;
                        editor.setText(selectedValue[0]);
                        isSettingText[0] = false;
                    }
                }
            }
        });

        // 用 StackPane 包装：下层 combo（全宽），上层 editor（右侧留空让箭头可点击）
        StackPane wrapper = new StackPane();
        wrapper.setPrefWidth(180);
        wrapper.setPrefHeight(30);

        // editor 右侧留出约26px的空隙给箭头按钮
        editor.setMaxWidth(180 - 26);
        editor.prefWidthProperty().bind(wrapper.widthProperty().subtract(26));
        StackPane.setAlignment(editor, Pos.CENTER_LEFT);

        wrapper.getChildren().addAll(combo, editor);
        return wrapper;
    }

    /**
     * 从 ComboBox 的显示文本中提取 CAMEO 国家代码
     * <p>
     * 支持："中国 (CHN)" → "CHN"，输入"CHN" → "CHN"，输入中文名自动匹配第一个，"全部"→null
     * </p>
     */
    private String extractCountryCode(String displayText) {
        if (displayText == null || displayText.trim().isEmpty()
                || "全部".equals(displayText) || "无".equals(displayText)) {
            return null;
        }
        String text = displayText.trim();
        // 格式：名称 (CODE)
        if (text.matches(".*\\([A-Z]{3}\\).*")) {
            String code = text.replaceAll(".*\\(([A-Z]{3})\\).*", "$1");
            if (code.length() == 3) return code;
        }
        // 纯三位码
        if (text.matches("[A-Za-z]{3}")) {
            return text.toUpperCase();
        }
        return null;
    }

    /**
     * 确保 ComboBox 当前值为有效选择
     * <p>
     * 由于 ComboBox 已设为不可编辑模式（通过箭头从列表中选择），
     * 正常情况下值一定是有效的。此方法仅在查询前做兜底校验。
     * </p>
     */
    private void ensureValidSelection(ComboBox<String> combo) {
        String value = combo.getValue();
        if (value == null || value.trim().isEmpty() || "无".equals(value.trim())) {
            // 如果值为空，设为列表第一项
            List<String> items = combo.getItems();
            if (items != null && !items.isEmpty()) {
                combo.setValue(items.get(0));
            }
        }
    }

    /**
     * 创建查询结果展示区域（SplitPane 分栏布局）
     * <p>
     * 左侧：TableView 表格展示事件明细（7 列），
     * 右侧：Canvas 饼图展示事件类型分布（使用 ChartUtils.drawPieChart）。
     * 分割比例 6:4。
     * </p>
     *
     * @return 结果展示区域 VBox
     */
    private VBox createResultBox() {
        VBox box = new VBox(10);

        // 结果表格
        resultTable = new TableView<>();

        TableColumn<Map<String, String>, String> dateCol = new TableColumn<>("日期");
        dateCol.setPrefWidth(100);
        dateCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("date")));

        TableColumn<Map<String, String>, String> actor1Col = new TableColumn<>("参与国1");
        actor1Col.setPrefWidth(120);
        actor1Col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("actor1")));

        TableColumn<Map<String, String>, String> actor2Col = new TableColumn<>("参与国2");
        actor2Col.setPrefWidth(120);
        actor2Col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("actor2")));

        TableColumn<Map<String, String>, String> eventCol = new TableColumn<>("事件编码");
        eventCol.setPrefWidth(70);
        eventCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("eventCode")));

        TableColumn<Map<String, String>, String> subTypeCol = new TableColumn<>("事件子类");
        subTypeCol.setPrefWidth(120);
        subTypeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("subType")));

        TableColumn<Map<String, String>, String> goldsteinCol = new TableColumn<>("Goldstein");
        goldsteinCol.setPrefWidth(80);
        goldsteinCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("goldstein")));
        goldsteinCol.setCellFactory(col -> new TableCell<Map<String, String>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    String color = getTableView().getItems().get(getIndex()).get("goldsteinColor");
                    setStyle("-fx-text-fill: " + (color != null ? color : "#7f8c8d") + "; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<Map<String, String>, String> toneCol = new TableColumn<>("情感语调");
        toneCol.setPrefWidth(80);
        toneCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("tone")));
        toneCol.setCellFactory(col -> new TableCell<Map<String, String>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    String color = getTableView().getItems().get(getIndex()).get("toneColor");
                    setStyle("-fx-text-fill: " + (color != null ? color : "#7f8c8d") + "; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<Map<String, String>, String> urlCol = new TableColumn<>("事件链接");
        urlCol.setPrefWidth(200);
        urlCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().get("url")));
        urlCol.setCellFactory(col -> new TableCell<Map<String, String>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #2980b9; -fx-underline: true; -fx-cursor: hand;");
                    setOnMouseClicked(e -> {
                        if (item != null && !item.isEmpty()) {
                            try {
                                java.awt.Desktop.getDesktop().browse(new java.net.URI(item));
                            } catch (Exception ex) {
                                System.err.println("[QueryPanel] 无法打开链接: " + ex.getMessage());
                            }
                        }
                    });
                }
            }
        });

        resultTable.getColumns().addAll(dateCol, actor1Col, actor2Col, eventCol, subTypeCol, goldsteinCol, toneCol, urlCol);

        // 翻页控制区域
        HBox paginationBox = new HBox(10);
        paginationBox.setAlignment(Pos.CENTER);
        paginationBox.setPadding(new Insets(5, 0, 5, 0));

        prevPageBtn = new Button("◀ 上一页");
        prevPageBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 12px;");
        prevPageBtn.setOnAction(e -> goToPage(currentPage - 1));

        pageInfoLabel = new Label("第 1 页 / 共 1 页");
        pageInfoLabel.setFont(Font.font("Microsoft YaHei", 12));

        nextPageBtn = new Button("下一页 ▶");
        nextPageBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 12px;");
        nextPageBtn.setOnAction(e -> goToPage(currentPage + 1));

        paginationBox.getChildren().addAll(prevPageBtn, pageInfoLabel, nextPageBtn);

        // 饼图
        pieCanvas = new Canvas(800, 380);
        pieCanvas.setWidth(800);
        pieCanvas.setHeight(380);

        // 初始提示文字
        GraphicsContext initGc = pieCanvas.getGraphicsContext2D();
        initGc.setFill(Color.GRAY);
        initGc.setFont(new Font("Microsoft YaHei", 16));
        initGc.setTextAlign(TextAlignment.CENTER);
        initGc.fillText("暂无数据，请执行查询", 400, 190);

        StackPane pieContainer = new StackPane(pieCanvas);
        pieContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");
        pieContainer.setPadding(new Insets(5));
        pieContainer.setAlignment(Pos.CENTER);

        // 饼图标题（放在饼图容器顶部，居中，类似表格列标题）
        pieTitleLabel = new Label("事件子类分布");
        pieTitleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
        pieTitleLabel.setPadding(new Insets(4, 0, 4, 0));
        pieTitleLabel.setAlignment(Pos.CENTER);
        pieTitleLabel.setMaxWidth(Double.MAX_VALUE);
        pieTitleLabel.setStyle("-fx-background-color: #f5f7fa; -fx-border-color: transparent transparent #d3d3d3 transparent; -fx-border-width: 0 0 1 0;");

        // 用 HBox 居中整个饼图区域
        VBox pieWithTitle = new VBox(5, pieTitleLabel, pieContainer);
        pieWithTitle.setPrefWidth(820);
        pieWithTitle.setMaxWidth(820);
        pieWithTitle.setPrefHeight(440);
        pieWithTitle.setMaxHeight(440);
        HBox pieCenterWrapper = new HBox(pieWithTitle);
        pieCenterWrapper.setAlignment(Pos.CENTER);

        // 饼图容器也固定高度
        pieContainer.setPrefHeight(410);
        pieContainer.setMaxHeight(410);

        // 上下布局：表格（固定350px）在上，翻页在中间，饼图（固定300px）在下
        resultTable.setPrefHeight(350);
        resultTable.setMaxHeight(350);
        VBox tableWithPagination = new VBox(5, resultTable, paginationBox);

        box.getChildren().addAll(tableWithPagination, pieCenterWrapper);
        return box;
    }

    /**
     * 执行查询操作 - 从后端数据库 bri.db 查询事件数据
     */
    private void performQuery() {
        // 查询前先确保两个参与国下拉框为有效选择
        ensureValidSelection(country1Combo);
        ensureValidSelection(country2Combo);

        String selected1 = country1Combo.getValue();
        String selected2 = country2Combo.getValue();
        String region = regionCombo.getValue();
        String eventType = eventTypeCombo.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null) startDate = LocalDate.now().minusYears(1);
        if (endDate == null) endDate = LocalDate.now();

        String actor1 = extractCountryCode(selected1);
        String actor2 = extractCountryCode(selected2);

        String regionKey = null;
        if (region != null && !region.equals("全部")) {
            Map<String, String> regionMap = new HashMap<>();
            regionMap.put("东亚", "EA");
            regionMap.put("东南亚", "SEA");
            regionMap.put("中亚", "CA");
            regionMap.put("南亚", "SA");
            regionMap.put("中东", "ME");
            regionMap.put("欧洲", "EU");
            regionMap.put("非洲", "AF");
            regionMap.put("北美洲", "NAM");
            regionMap.put("南美洲", "SAM");
            regionMap.put("大洋洲", "OCE");
            regionKey = regionMap.get(region);
        }

        List<GDELTEvent> result = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        try {
            // 获取 DaoFactory
            DaoFactory df;
            try {
                df = DaoFactory.getInstance();
            } catch (IllegalStateException e) {
                DataSource ds = DataSource.getInstance("bri.db");
                ds.initIfAbsent();
                df = DaoFactory.getInstance("bri.db");
            }
            EventDao eventDao = df.eventDao();

            // 构建查询条件（仅基础过滤）
            EventQuery.Builder qb = new EventQuery.Builder()
                .dateFrom(startDate.toString())
                .dateTo(endDate.toString())
                .limit(5000);

            if (actor1 != null) qb.actor1(actor1);
            if (actor2 != null) qb.actor2(actor2);
            if (regionKey != null) qb.subregion(regionKey);

            List<Event> rawEvents = eventDao.query(qb.build());

            // 获取子类型选择
            String subEventType = subEventTypeCombo.getValue();
            String mainCategory = eventType;

            // 数据库层过滤策略：
            // 选择合作/冲突时，先用 queryByRoots 在数据库按大类过滤（含日期），
            // 再与 rawEvents（含国家/区域/日期条件）取交集，确保所有条件都满足
            // 选择"全部"时直接使用 rawEvents
            List<Event> filteredByMain;
            if (mainCategory != null && mainCategory.contains("合作事件")) {
                List<Event> rootsEvents = eventDao.queryByRoots(
                    List.of("01", "02", "03", "04", "05", "06", "07", "16", "17", "18", "19", "20"),
                    startDate.toString(), endDate.toString());
                // 取交集：同时满足大类和国家/区域条件
                Set<Long> rawIds = rawEvents.stream().map(Event::getEventId).collect(Collectors.toSet());
                filteredByMain = rootsEvents.stream()
                    .filter(e -> rawIds.contains(e.getEventId()))
                    .collect(Collectors.toList());
            } else if (mainCategory != null && mainCategory.contains("冲突事件")) {
                List<Event> rootsEvents = eventDao.queryByRoots(
                    List.of("08", "09", "10", "11", "12", "13", "14", "21", "22"),
                    startDate.toString(), endDate.toString());
                // 取交集
                Set<Long> rawIds = rawEvents.stream().map(Event::getEventId).collect(Collectors.toSet());
                filteredByMain = rootsEvents.stream()
                    .filter(e -> rawIds.contains(e.getEventId()))
                    .collect(Collectors.toList());
            } else if (mainCategory != null && mainCategory.contains("竞争事件")) {
                List<Event> rootsEvents = eventDao.queryByRoots(
                    List.of("15"),
                    startDate.toString(), endDate.toString());
                Set<Long> rawIds = rawEvents.stream().map(Event::getEventId).collect(Collectors.toSet());
                filteredByMain = rootsEvents.stream()
                    .filter(e -> rawIds.contains(e.getEventId()))
                    .collect(Collectors.toList());
            } else {
                filteredByMain = rawEvents;
            }

            // 对 filteredByMain 按 sourceUrl 去重
            Set<String> mainDedup = new HashSet<>();
            List<Event> uniqueFiltered = new ArrayList<>();
            for (Event e : filteredByMain) {
                String url = e.getSourceUrl();
                String key = (url != null && !url.trim().isEmpty()) ? url.trim() : e.getEventDate() + "_" + e.getActor1Code() + "_" + e.getActor2Code() + "_" + e.getEventRoot();
                if (mainDedup.add(key)) {
                    uniqueFiltered.add(e);
                }
            }
            filteredByMain = uniqueFiltered;

            // 在内存中按子类型过滤
            for (Event e : filteredByMain) {
                String root = e.getEventRoot();

                // 从 event_root 推导 rootCode
                Integer rootCode = null;
                if (root != null && !root.trim().isEmpty()) {
                    try {
                        rootCode = Integer.parseInt(root.trim());
                    } catch (NumberFormatException ignored) {}
                }

                // 按子类型过滤
                boolean matchSub = true;
                if (subEventType != null && !"全部".equals(subEventType) && rootCode != null) {
                    String codeStr = String.format("%02d", rootCode);
                    String expectedCode = null;
                    if (mainCategory != null && mainCategory.contains("合作事件")) {
                        expectedCode = COOPERATION_SUB_TYPES.get(subEventType);
                    } else if (mainCategory != null && mainCategory.contains("冲突事件")) {
                        expectedCode = CONFLICT_SUB_TYPES.get(subEventType);
                    } else if (mainCategory != null && mainCategory.contains("竞争事件")) {
                        expectedCode = COMPETITION_SUB_TYPES.get(subEventType);
                    }
                    matchSub = expectedCode != null && codeStr.equals(expectedCode);
                }

                if (matchSub) {
                    // 计算子类型显示名
                    String subTypeDisplay = "";
                    if (rootCode != null) {
                        String codeStr = String.format("%02d", rootCode);
                        if (mainCategory != null && mainCategory.contains("合作事件")) {
                            for (Map.Entry<String, String> entry : COOPERATION_SUB_TYPES.entrySet()) {
                                if (codeStr.equals(entry.getValue())) {
                                    subTypeDisplay = entry.getKey();
                                    break;
                                }
                            }
                            if (subTypeDisplay.isEmpty()) {
                                subTypeDisplay = "合作-" + codeStr;
                            }
                        } else if (mainCategory != null && mainCategory.contains("冲突事件")) {
                            for (Map.Entry<String, String> entry : CONFLICT_SUB_TYPES.entrySet()) {
                                if (codeStr.equals(entry.getValue())) {
                                    subTypeDisplay = entry.getKey();
                                    break;
                                }
                            }
                            if (subTypeDisplay.isEmpty()) {
                                subTypeDisplay = "冲突-" + codeStr;
                            }
                        } else if (mainCategory != null && mainCategory.contains("竞争事件")) {
                            for (Map.Entry<String, String> entry : COMPETITION_SUB_TYPES.entrySet()) {
                                if (codeStr.equals(entry.getValue())) {
                                    subTypeDisplay = entry.getKey();
                                    break;
                                }
                            }
                            if (subTypeDisplay.isEmpty()) {
                                subTypeDisplay = "竞争-" + codeStr;
                            }
                        }
                    }

                    GDELTEvent gdelt = new GDELTEvent(
                        e.getEventDate() != null && e.getEventDate().length() >= 10
                            ? LocalDate.parse(e.getEventDate().substring(0, 10))
                            : LocalDate.now(),
                        e.getActor1Code(),
                        e.getActor2Code(),
                        e.getEventRoot(),
                        e.getGoldstein(),
                        e.getNumMentions(),
                        e.getAvgTone()
                    );
                    gdelt.setQuadClass(null);
                    gdelt.setEventCategoryName(subTypeDisplay);
                    gdelt.setSourceUrl(e.getSourceUrl());
                    // 按 sourceUrl 去重（相同链接视为同一事件）
                    String url = e.getSourceUrl();
                    String dedupKey = (url != null && !url.trim().isEmpty()) ? url.trim() : e.getEventDate() + "_" + e.getActor1Code() + "_" + e.getActor2Code() + "_" + e.getEventRoot();
                    if (seenUrls.add(dedupKey)) {
                        result.add(gdelt);
                    }
                }
            }

        } catch (Exception ex) {
            System.err.println("[QueryPanel] 数据库查询失败: " + ex.getMessage());
            resultCountLabel.setText("查询失败: " + ex.getMessage());
            return;
        }

        // 填充表格
        System.err.println("[QueryPanel] result.size=" + result.size());
        for (GDELTEvent ev : result) {
            System.err.println("  ev: actor1=" + ev.getActor1Code() + " root=" + ev.getEventCode() + " name='" + ev.getEventCategoryName() + "'");
        }

        resultTable.getItems().clear();
        allQueryResults = result;
        currentPage = 0;
        displayCurrentPage();

        resultCountLabel.setText("查询结果: " + result.size() + " 条记录");

        // 绘制事件类型饼图
        drawEventTypePie(result);
    }

    /**
     * 显示当前页数据到表格
     */
    private void displayCurrentPage() {
        resultTable.getItems().clear();
        if (allQueryResults == null || allQueryResults.isEmpty()) {
            pageInfoLabel.setText("第 0 页 / 共 0 页");
            prevPageBtn.setDisable(true);
            nextPageBtn.setDisable(true);
            return;
        }

        int totalPages = (int) Math.ceil((double) allQueryResults.size() / PAGE_SIZE);
        if (currentPage < 0) currentPage = 0;
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        int from = currentPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, allQueryResults.size());

        for (int i = from; i < to; i++) {
            GDELTEvent event = allQueryResults.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("date", event.getDate().toString());
            String n1 = BRICountries.getCountryName(event.getActor1Code());
            row.put("actor1", (n1 != null ? n1 : event.getActor1Code()) + " (" + event.getActor1Code() + ")");
            String n2 = BRICountries.getCountryName(event.getActor2Code());
            row.put("actor2", event.getActor2Code() != null && !event.getActor2Code().isEmpty()
                ? (n2 != null ? n2 : event.getActor2Code()) + " (" + event.getActor2Code() + ")"
                : "-");
            row.put("eventCode", event.getEventCode());
            row.put("subType", event.getEventCategoryName());
            double gs = event.getGoldsteinScale();
            row.put("goldstein", String.format("%.1f", gs));
            row.put("goldsteinColor", gs > 0 ? "#27ae60" : (gs < 0 ? "#e74c3c" : "#7f8c8d"));
            double tone = event.getAvgTone();
            row.put("tone", String.format("%.2f", tone));
            row.put("toneColor", tone > 0 ? "#27ae60" : (tone < 0 ? "#e74c3c" : "#7f8c8d"));
            row.put("url", event.getSourceUrl() != null ? event.getSourceUrl() : "");
            resultTable.getItems().add(row);
        }

        pageInfoLabel.setText("第 " + (currentPage + 1) + " 页 / 共 " + totalPages + " 页");
        prevPageBtn.setDisable(currentPage <= 0);
        nextPageBtn.setDisable(currentPage >= totalPages - 1);
    }

    /**
     * 跳转到指定页
     */
    private void goToPage(int page) {
        if (allQueryResults == null || allQueryResults.isEmpty()) return;
        int totalPages = (int) Math.ceil((double) allQueryResults.size() / PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        currentPage = page;
        displayCurrentPage();
    }

    /**
     * 绘制事件子类分布饼图（按 eventCategoryName 分组）
     */
    private void drawEventTypePie(List<GDELTEvent> events) {
        GraphicsContext gc = pieCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, 800, 380);

        // 更新饼图标题
        String mainCategory = eventTypeCombo.getValue();
        String pieTitle = "事件子类分布";
        if (mainCategory != null && mainCategory.contains("合作事件")) {
            pieTitle = "合作事件子类分布";
        } else if (mainCategory != null && mainCategory.contains("冲突事件")) {
            pieTitle = "冲突事件子类分布";
        } else if (mainCategory != null && mainCategory.contains("竞争事件")) {
            pieTitle = "竞争事件子类分布";
        }
        pieTitleLabel.setText(pieTitle);

        if (events == null || events.isEmpty()) {
            gc.setFill(Color.GRAY);
            gc.setFont(new Font("Microsoft YaHei", 16));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("暂无数据", 400, 190);
            return;
        }

        // 按子类名分组统计
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (GDELTEvent ev : events) {
            String name = ev.getEventCategoryName();
            if (name == null || name.trim().isEmpty()) name = "未知";
            typeCount.merge(name, 1L, Long::sum);
        }
        if (typeCount.isEmpty()) return;

        List<String> labels = new ArrayList<>(typeCount.keySet());
        List<Double> vals = new ArrayList<>();
        for (String k : labels) vals.add(typeCount.get(k).doubleValue());

        double total = 0;
        for (double v : vals) total += v;
        if (total <= 0) return;

        Color[] COLORS = {
            Color.web("#3498db"), Color.web("#2ecc71"), Color.web("#f1c40f"),
            Color.web("#e74c3c"), Color.web("#9b59b6"), Color.web("#1abc9c"),
            Color.web("#e67e22"), Color.web("#34495e")
        };

        // ===== 饼图（居中在画布正中心） =====
        double cx = 390, cy = 190, r = 140;
        double angle = 0;
        for (int i = 0; i < labels.size(); i++) {
            double sweep = vals.get(i) / total * 360;
            gc.setFill(COLORS[i % COLORS.length]);
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, angle - 90, sweep, ArcType.ROUND);
            angle += sweep;
        }

        // 描边
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        angle = 0;
        for (int i = 0; i < labels.size(); i++) {
            double sweep = vals.get(i) / total * 360;
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, angle - 90, sweep, ArcType.ROUND);
            angle += sweep;
        }

        // ===== 百分比标签（全部在饼图外侧） =====
        angle = 0;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(new Font(11));
        for (int i = 0; i < labels.size(); i++) {
            double sweep = vals.get(i) / total * 360;
            double mid = angle + sweep / 2;
            double rad = Math.toRadians(mid - 90);
            double lx = cx + (r + 18) * Math.cos(rad);
            double ly = cy + (r + 18) * Math.sin(rad);
            gc.setFill(Color.rgb(40, 40, 40));
            gc.fillText(String.format("%.1f%%", vals.get(i) / total * 100), lx, ly + 4);
            angle += sweep;
        }

        // ===== 图例（右侧居中，与饼图整体平衡） =====
        double legendStartX = 570;
        int itemCount = labels.size();
        double totalLegendHeight = itemCount * 24;
        double legendStartY = (380 - totalLegendHeight) / 2;

        gc.setFont(new Font("Microsoft YaHei", 11));
        gc.setTextAlign(TextAlignment.LEFT);
        for (int i = 0; i < itemCount; i++) {
            double y = legendStartY + i * 24;
            gc.setFill(COLORS[i % COLORS.length]);
            gc.fillRoundRect(legendStartX, y, 12, 12, 3, 3);
            gc.setFill(Color.rgb(50, 50, 50));
            String pct = String.format("%.1f%%", vals.get(i) / total * 100);
            gc.fillText(labels.get(i) + "  " + pct, legendStartX + 18, y + 10);
        }
    }

    /**
     * 清空所有查询条件并重置界面
     * <p>
     * 将所有下拉框重置到默认选项（"全部"或第一项），
     * 日期范围恢复为数据库中实际数据范围，
     * 清空表格数据和饼图。
     * </p>
     */
    private void clearQuery() {
        // 参与国1重置为"中国 (CHN)"，参与国2重置为"全部"
        // 同时恢复完整候选列表
        List<String> c1Items = new ArrayList<>();
        c1Items.add("全部");
        c1Items.add("中国 (CHN)");
        List<BRICountries.CountryInfo> infos = new ArrayList<>(BRICountries.getAllCountries());
        infos.sort(Comparator.comparing(BRICountries.CountryInfo::getCode));
        for (BRICountries.CountryInfo info : infos) {
            if (!"CHN".equals(info.getCode())) {
                c1Items.add(info.getName() + " (" + info.getCode() + ")");
            }
        }
        country1Combo.getItems().setAll(c1Items);
        country1Combo.setValue("中国 (CHN)");

        List<String> c2Items = new ArrayList<>(c1Items);
        country2Combo.getItems().setAll(c2Items);
        country2Combo.setValue("全部");

        regionCombo.getSelectionModel().select(0);
        eventTypeCombo.getSelectionModel().select(0);
        subEventTypeCombo.getItems().clear();
        subEventTypeCombo.getItems().add("全部");
        subEventTypeCombo.getSelectionModel().select(0);
        startDatePicker.setValue(LocalDate.now().minusMonths(1));
        endDatePicker.setValue(LocalDate.now());
        resultTable.getItems().clear();
        allQueryResults = null;
        currentPage = 0;
        pageInfoLabel.setText("第 0 页 / 共 0 页");
        prevPageBtn.setDisable(true);
        nextPageBtn.setDisable(true);
        resultCountLabel.setText("查询结果: 0 条记录");
        pieCanvas.getGraphicsContext2D().clearRect(0, 0, pieCanvas.getWidth(), pieCanvas.getHeight());
    }
}
