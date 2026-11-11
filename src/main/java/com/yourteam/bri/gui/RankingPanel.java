
package com.yourteam.bri.gui;

import com.yourteam.bri.model.CooperationIndex;
import com.yourteam.bri.service.DataService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 合作指数排行榜面板 - 以柱状图展示一带一路沿线国家的合作指数排名（对应需求 F-20）
 * <p>
 * 功能覆盖：
 * <ul>
 * <li>区域筛选：支持按区域（东南亚、中亚、中东、欧洲、非洲、南亚）过滤</li>
 * <li>柱状图展示：使用 ChartUtils.drawBarChart 绘制，默认显示 TOP 15</li>
 * <li>区域颜色编码：每个区域有独立的主题色（东南亚绿、中亚蓝、中东橙、欧洲紫、非洲红、南亚青）</li>
 *   <li>底部图例：显示各区域的颜色映射关系</li>
 * </ul>
 * </p>
 * <p>
 * 数据来源：DataService.getCooperationRankings()，包含合作指数、冲突占比、
 * Goldstein 均值、情感语调、媒体关注度等多维度信息，当前柱状图展示综合合作指数。
 * </p>
 *
 * @see ChartUtils#drawBarChart 底层柱状图绘制方法
 * @see com.yourteam.bri.service.DataService#getCooperationRankings() 数据源
 * @see com.yourteam.bri.model.CooperationIndex 排名数据模型
 */
public class RankingPanel extends VBox {

    private Canvas rankingCanvas;
    private ComboBox<String> regionFilter;
    private List<CooperationIndex> allRankings;

    // 区域颜色映射
    private static final Map<String, Color> REGION_COLORS = new LinkedHashMap<>();
    static {
        REGION_COLORS.put("东亚", Color.rgb(52, 73, 94));
        REGION_COLORS.put("东南亚", Color.rgb(46, 204, 113));
        REGION_COLORS.put("中亚", Color.rgb(52, 152, 219));
        REGION_COLORS.put("南亚", Color.rgb(26, 188, 156));
        REGION_COLORS.put("中东", Color.rgb(243, 156, 18));
        REGION_COLORS.put("欧洲", Color.rgb(155, 89, 182));
        REGION_COLORS.put("非洲", Color.rgb(231, 76, 60));
        REGION_COLORS.put("北美洲", Color.rgb(230, 126, 34));
        REGION_COLORS.put("南美洲", Color.rgb(22, 160, 133));
        REGION_COLORS.put("大洋洲", Color.rgb(41, 128, 185));
        }

    public RankingPanel() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: #f5f7fa;");

        // 标题
        Label titleLabel = new Label("\uD83C\uDFC6 合作指数排行榜");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e1e3c"));

        // 控制栏
        HBox controlBar = new HBox(15);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(10));
        controlBar.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        Label filterLabel = new Label("区域筛选:");
        filterLabel.setFont(Font.font("Microsoft YaHei", 13));

        regionFilter = new ComboBox<>();
        regionFilter.setPrefWidth(150);
        regionFilter.setStyle("-fx-font-size: 13px;");
        regionFilter.getItems().add("全部区域");
        for (String region : new String[]{"东亚", "东南亚", "中亚", "南亚", "中东", "欧洲", "非洲", "北美洲", "南美洲", "大洋洲"}) {
            regionFilter.getItems().add(region);
        }
        regionFilter.getSelectionModel().select(0);
        regionFilter.setOnAction(e -> updateRanking());

        Label sortLabel = new Label("排序依据: 综合合作指数");
        sortLabel.setFont(Font.font("Microsoft YaHei", 12));
        sortLabel.setTextFill(Color.GRAY);

        Label infoLabel = new Label("\uD83C\uDF08 颜色按区域区分");
        infoLabel.setFont(Font.font("Microsoft YaHei", 11));
        infoLabel.setTextFill(Color.GRAY);

        controlBar.getChildren().addAll(filterLabel, regionFilter, sortLabel, infoLabel);

        // 图表画布
        rankingCanvas = new Canvas(800, 500);
        rankingCanvas.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        StackPane canvasContainer = new StackPane(rankingCanvas);
        canvasContainer.setPadding(new Insets(10));
        canvasContainer.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");

        // 图例
        HBox legendBox = new HBox(20);
        legendBox.setAlignment(Pos.CENTER);
        legendBox.setPadding(new Insets(8));
        legendBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);");

        for (Map.Entry<String, Color> entry : REGION_COLORS.entrySet()) {
            HBox item = new HBox(5);
            item.setAlignment(Pos.CENTER_LEFT);

            Region colorBlock = new Region();
            colorBlock.setPrefSize(14, 14);
            colorBlock.setStyle("-fx-background-color: " + toHex(entry.getValue()) + "; -fx-background-radius: 3;");

            Label nameLabel = new Label(entry.getKey());
            nameLabel.setFont(Font.font("Microsoft YaHei", 11));

            item.getChildren().addAll(colorBlock, nameLabel);
            legendBox.getChildren().add(item);
        }

        getChildren().addAll(titleLabel, controlBar, canvasContainer, legendBox);

        // 初始加载
        allRankings = DataService.getCooperationRankings();
        updateRanking();
    }

    /**
     * 更新排行榜 - 根据区域筛选条件重绘柱状图
     * <p>
     * 处理流程：
     * <ol>
     *   <li>获取当前选中的区域筛选值</li>
     *   <li>过滤 allRankings 数据（"全部区域"则不过滤）</li>
     *   <li>按合作指数降序排列，取前 15 名</li>
     *   <li>提取标签、数值、颜色映射</li>
     *   <li>调用 ChartUtils.drawBarChart 重绘</li>
     * </ol>
     * </p>
     */
    private void updateRanking() {
        String selectedRegion = regionFilter.getValue();
        List<CooperationIndex> filtered;

        if (selectedRegion == null || selectedRegion.equals("全部区域")) {
            filtered = allRankings;
        } else {
            filtered = allRankings.stream()
                .filter(r -> r.getRegion().equals(selectedRegion))
                .collect(Collectors.toList());
        }

        // 按合作指数降序排序，取前15
        filtered.sort((a, b) -> Double.compare(b.getIndexValue(), a.getIndexValue()));
        List<CooperationIndex> topN = filtered.size() > 15 ? filtered.subList(0, 15) : filtered;

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Color> barColors = new ArrayList<>();

        for (CooperationIndex idx : topN) {
            labels.add(idx.getCountryName());
            values.add(idx.getIndexValue());
            Color c = REGION_COLORS.getOrDefault(idx.getRegion(), Color.GRAY);
            barColors.add(c);
        }

        ChartUtils.drawBarChart(rankingCanvas, labels, values, barColors,
            "一带一路沿线国家合作指数排行榜 (TOP " + topN.size() + ")", "合作指数");
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
}
