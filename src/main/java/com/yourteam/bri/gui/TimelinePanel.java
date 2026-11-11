package com.yourteam.bri.gui;

import com.yourteam.bri.model.TimeRange;
import com.bri.dao.DaoFactory;
import com.bri.dao.EventDao;
import com.bri.config.DataSource;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.LocalDate;
import java.util.*;

/**
 * 时间轴面板 - 底部交互式时间轴，支持双滑块选择时间范围与自动播放功能
 *
 * <p>时间范围来自数据库 {@link EventDao#getDateRange()} 的实际数据范围。
 * 关键事件从数据库中按月度事件量和 Goldstein 均值自动发现。</p>
 */
public class TimelinePanel extends VBox {

    /** 支持的最小日期（从数据库获取） */
    private LocalDate minDate;
    /** 支持的最大日期（从数据库获取） */
    private LocalDate maxDate;
    /** 当前选中的起始日期 */
    private LocalDate startDate;
    /** 当前选中的结束日期 */
    private LocalDate endDate;

    // UI组件
    private Canvas timelineCanvas;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Label currentDateLabel;
    private Button playPauseBtn;
    private ComboBox<Integer> speedCombo;
    private Label titleLabel;
    private HBox controlRow;

    // 滑块状态
    private double sliderWidth;
    /** 时间轴画布高度 */
    private static final double SLIDER_HEIGHT = 50;
    /** 滑块手柄半径（像素）*/
    private static final double HANDLE_RADIUS = 10;
    /** 轨道 Y 坐标 */
    private static final double TRACK_Y = 25;
    /** 轨道高度 */
    private static final double TRACK_HEIGHT = 6;

    // 拖动状态
    private enum DragTarget { NONE, START_HANDLE, END_HANDLE }
    private DragTarget currentDrag = DragTarget.NONE;

    // 播放状态
    private boolean isPlaying = false;
    private int playSpeed = 1; // 1, 2, 4
    private AnimationTimer playTimer;
    private long lastPlayUpdate = 0;
    /** 播放时每次前进的天数（30天 * 速度倍数） */
    private static final long PLAY_STEP_DAYS = 30;

    // 回调监听器
    private TimelineChangeListener listener;

    // 数据库日期范围是否已加载
    private volatile boolean dbReady = false;

    /**
     * 时间轴变化监听器
     */
    public interface TimelineChangeListener {
        void onTimeRangeChanged(LocalDate start, LocalDate end);
        void onPlayStateChanged(boolean isPlaying);
    }

    public TimelinePanel() {
        // 初始化默认值（加载数据库后会被覆盖）
        minDate = LocalDate.of(2020, 1, 1);
        maxDate = LocalDate.now();
        startDate = minDate;
        endDate = maxDate;

        setPadding(new Insets(8, 15, 8, 15));
        setSpacing(6);
        setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 4, 0, 0, 2);"
        );

        // 标题行
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        titleLabel = new Label("\u23F1 时间轴");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

        currentDateLabel = new Label(formatDateRange(startDate, endDate));
        currentDateLabel.setFont(Font.font("Microsoft YaHei", 11));
        currentDateLabel.setTextFill(Color.web("#1a3a5c"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 可用年份指示（先显示加载中，数据库就绪后更新）
        Label rangeLabel = new Label("加载数据库中...");
        rangeLabel.setFont(Font.font("Microsoft YaHei", 10));
        rangeLabel.setTextFill(Color.GRAY);

        headerRow.getChildren().addAll(titleLabel, currentDateLabel, spacer, rangeLabel);

        // 时间轴画布
        timelineCanvas = new Canvas(800, SLIDER_HEIGHT);
        timelineCanvas.setStyle("-fx-cursor: hand;");

        // 滑块事件
        timelineCanvas.setOnMousePressed(this::handleMousePressed);
        timelineCanvas.setOnMouseDragged(this::handleMouseDragged);
        timelineCanvas.setOnMouseReleased(e -> currentDrag = DragTarget.NONE);
        timelineCanvas.setOnMouseMoved(this::handleMouseMoved);

        // 滑块容器
        StackPane canvasContainer = new StackPane(timelineCanvas);
        canvasContainer.setPadding(new Insets(0, 20, 0, 20));

        // 日期选择器（可手动输入，随滑块移动联动）
        HBox dateRow = new HBox(10);
        dateRow.setAlignment(Pos.CENTER);

        Label fromLabel = new Label("从:");
        fromLabel.setFont(Font.font("Microsoft YaHei", 10));
        fromLabel.setTextFill(Color.web("#555"));

        startDatePicker = new DatePicker(startDate);
        startDatePicker.setPrefWidth(150);
        startDatePicker.setStyle("-fx-font-size: 11px;");

        Label dashLabel = new Label("—");
        dashLabel.setFont(Font.font(12));
        dashLabel.setTextFill(Color.GRAY);

        Label toLabel = new Label("到:");
        toLabel.setFont(Font.font("Microsoft YaHei", 10));
        toLabel.setTextFill(Color.web("#555"));

        endDatePicker = new DatePicker(endDate);
        endDatePicker.setPrefWidth(150);
        endDatePicker.setStyle("-fx-font-size: 11px;");

        // DatePicker 值变化时联动更新
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                LocalDate val = newVal;
                // 限制在数据库范围内
                if (val.isBefore(minDate)) val = minDate;
                if (val.isAfter(maxDate)) val = maxDate;
                // 不能晚于结束日期
                if (val.isAfter(endDate)) val = endDate;
                startDate = val;
                startDatePicker.setValue(startDate);
                startDatePicker.getEditor().setText(startDate.toString());
                currentDateLabel.setText(formatDateRange(startDate, endDate));
                drawTimeline();
                if (listener != null) listener.onTimeRangeChanged(startDate, endDate);
            }
        });
        // 手动输入按回车时提交
        startDatePicker.getEditor().setOnAction(e -> {
            String text = startDatePicker.getEditor().getText();
            try {
                LocalDate parsed = LocalDate.parse(text);
                if (!parsed.isBefore(minDate) && !parsed.isAfter(maxDate)) {
                    startDatePicker.setValue(parsed);
                } else {
                    startDatePicker.getEditor().setText(startDate.toString());
                }
            } catch (Exception ex) {
                startDatePicker.getEditor().setText(startDate.toString());
            }
        });
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                LocalDate val = newVal;
                // 限制在数据库范围内
                if (val.isBefore(minDate)) val = minDate;
                if (val.isAfter(maxDate)) val = maxDate;
                // 不能早于起始日期
                if (val.isBefore(startDate)) val = startDate;
                endDate = val;
                endDatePicker.setValue(endDate);
                endDatePicker.getEditor().setText(endDate.toString());
                currentDateLabel.setText(formatDateRange(startDate, endDate));
                drawTimeline();
                if (listener != null) listener.onTimeRangeChanged(startDate, endDate);
            }
        });
        // 手动输入按回车时提交
        endDatePicker.getEditor().setOnAction(e -> {
            String text = endDatePicker.getEditor().getText();
            try {
                LocalDate parsed = LocalDate.parse(text);
                if (!parsed.isBefore(minDate) && !parsed.isAfter(maxDate)) {
                    endDatePicker.setValue(parsed);
                } else {
                    endDatePicker.getEditor().setText(endDate.toString());
                }
            } catch (Exception ex) {
                endDatePicker.getEditor().setText(endDate.toString());
            }
        });

        dateRow.getChildren().addAll(fromLabel, startDatePicker, dashLabel, toLabel, endDatePicker);
        dateRow.setAlignment(Pos.CENTER);

        // 控制行
        controlRow = new HBox(10);
        controlRow.setAlignment(Pos.CENTER);

        // 播放/暂停按钮
        playPauseBtn = new Button("\u25B6 播放");
        playPauseBtn.setStyle(
            "-fx-font-size: 11px; -fx-background-color: #3498db; -fx-text-fill: white; " +
            "-fx-padding: 4 14; -fx-background-radius: 12; -fx-cursor: hand;"
        );
        playPauseBtn.setOnAction(e -> togglePlay());

        // 速度控制
        Label speedLabel = new Label("速度:");
        speedLabel.setFont(Font.font("Microsoft YaHei", 10));
        speedLabel.setTextFill(Color.GRAY);

        speedCombo = new ComboBox<>();
        speedCombo.getItems().addAll(1, 2, 4);
        speedCombo.setValue(1);
        speedCombo.setStyle("-fx-font-size: 10px; -fx-pref-width: 55px;");
        speedCombo.setOnAction(e -> {
            playSpeed = speedCombo.getValue();
        });

        // 重置按钮
        Button resetBtn = new Button("重置");
        resetBtn.setStyle(
            "-fx-font-size: 10px; -fx-background-color: #ecf0f1; -fx-text-fill: #555; " +
            "-fx-padding: 3 10; -fx-background-radius: 10; -fx-cursor: hand;"
        );
        resetBtn.setOnAction(e -> resetTimeRange());

        controlRow.getChildren().addAll(playPauseBtn, speedLabel, speedCombo, resetBtn);

        // 组装
        getChildren().addAll(headerRow, canvasContainer, dateRow, controlRow);

        // 从数据库加载日期范围和关键事件
        Platform.runLater(() -> loadFromDatabase());

        // 先绘制默认时间轴
        drawTimeline();
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

    /**
     * 从数据库加载日期范围和关键事件
     */
    private void loadFromDatabase() {
        new Thread(() -> {
            try {
                ensureDatabaseReady();
                DaoFactory df = DaoFactory.getInstance();
                EventDao eventDao = df.eventDao();

                // 获取数据库实际日期范围
                LocalDate[] dateRange = eventDao.getDateRange();
                if (dateRange == null || dateRange.length < 2 || dateRange[0] == null || dateRange[1] == null) {
                    Platform.runLater(() -> {
                        Label headerLabel = (Label) ((HBox) getChildren().get(0)).getChildren()
                            .get(((HBox) getChildren().get(0)).getChildren().size() - 1);
                        headerLabel.setText("数据范围: 无数据");
                    });
                    drawTimeline();
                    return;
                }

                minDate = dateRange[0];
                maxDate = dateRange[1];

                // 默认选中全部范围
                startDate = minDate;
                endDate = maxDate;

                Platform.runLater(() -> {
                    dbReady = true;

                    // 更新标题栏的日期范围
                    Label headerLabel = (Label) ((HBox) getChildren().get(0)).getChildren()
                        .get(((HBox) getChildren().get(0)).getChildren().size() - 1);
                    headerLabel.setText("数据范围: " + minDate.getYear() + " - " + maxDate.getYear());

                    // 更新日期选择器
                    startDatePicker.setValue(startDate);
                    startDatePicker.getEditor().setText(startDate.toString());
                    endDatePicker.setValue(endDate);
                    endDatePicker.getEditor().setText(endDate.toString());
                    // 设置日期范围限制
                    startDatePicker.setDayCellFactory(dp -> new DateCell() {
                        @Override
                        public void updateItem(LocalDate item, boolean empty) {
                            super.updateItem(item, empty);
                            setDisable(item.isBefore(minDate) || item.isAfter(maxDate));
                        }
                    });
                    endDatePicker.setDayCellFactory(dp -> new DateCell() {
                        @Override
                        public void updateItem(LocalDate item, boolean empty) {
                            super.updateItem(item, empty);
                            setDisable(item.isBefore(minDate) || item.isAfter(maxDate));
                        }
                    });
                    currentDateLabel.setText(formatDateRange(startDate, endDate));

                    // 重新绘制
                    drawTimeline();

                    // 通知监听器初始时间范围
                    if (listener != null) {
                        listener.onTimeRangeChanged(startDate, endDate);
                    }
                });
            } catch (Exception e) {
                System.err.println("[TimelinePanel] 数据库加载失败: " + e.getMessage());
                Platform.runLater(() -> {
                    Label headerLabel = (Label) ((HBox) getChildren().get(0)).getChildren()
                        .get(((HBox) getChildren().get(0)).getChildren().size() - 1);
                    headerLabel.setText("数据范围: " + minDate.getYear() + " - " + maxDate.getYear());
                    drawTimeline();
                });
            }
        }).start();
    }

    /**
     * 绘制时间轴画布（核心渲染方法）
     */
    private void drawTimeline() {
        GraphicsContext gc = timelineCanvas.getGraphicsContext2D();
        double w = timelineCanvas.getWidth();
        double h = timelineCanvas.getHeight();
        gc.clearRect(0, 0, w, h);

        sliderWidth = w - 40; // 左右留白

        double trackLeft = 20;
        double trackRight = trackLeft + sliderWidth;

        // 1. 绘制背景轨道
        gc.setFill(Color.rgb(230, 235, 245));
        gc.setGlobalAlpha(0.5);

        // 轨道阴影
        gc.setFill(Color.rgb(0, 0, 0, 0.05));
        gc.fillRoundRect(trackLeft, TRACK_Y + 2, sliderWidth, TRACK_HEIGHT, 3, 3);

        // 轨道
        gc.setFill(Color.rgb(220, 228, 240));
        gc.fillRoundRect(trackLeft, TRACK_Y, sliderWidth, TRACK_HEIGHT, 3, 3);

        // 2. 绘制选中区间（高亮轨道）
        double startX = dateToX(startDate);
        double endX = dateToX(endDate);

        gc.setGlobalAlpha(1.0);

        // 渐变效果
        LinearGradient gradient = new LinearGradient(startX, 0, endX, 0, false,
            CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#2980b9")),
            new Stop(1, Color.web("#3498db"))
        );
        gc.setFill(gradient);
        gc.fillRoundRect(startX, TRACK_Y, endX - startX, TRACK_HEIGHT, 3, 3);

        // 3. 绘制刻度线（年份）
        gc.setFill(Color.rgb(150, 160, 180));
        gc.setFont(new Font(9));
        gc.setGlobalAlpha(0.7);

        // 只显示涉及的年份
        Set<Integer> yearsInRange = new HashSet<>();
        for (int year = minDate.getYear(); year <= maxDate.getYear(); year++) {
            yearsInRange.add(year);
        }
        for (int year : yearsInRange) {
            LocalDate yearDate = LocalDate.of(year, 6, 1);
            // 确保不超过范围
            if (yearDate.isBefore(minDate)) yearDate = minDate;
            if (yearDate.isAfter(maxDate)) yearDate = maxDate;
            double x = dateToX(yearDate);
            gc.fillRect(x, TRACK_Y - 8, 1, 5);
            gc.fillText(String.valueOf(year), x - 8, TRACK_Y - 12);
        }

        gc.setGlobalAlpha(1.0);

        // 5. 绘制滑块手柄
        drawHandle(gc, startX, true);
        drawHandle(gc, endX, false);

        // 6. 绘制当前播放位置指示（播放模式下）
        if (isPlaying) {
            double curX = dateToX(endDate);
            gc.setFill(Color.web("#E74C3C"));
            gc.fillOval(curX - 4, TRACK_Y - 3, 8, 8);
        }
    }

    /**
     * 绘制单个滑块手柄（圆形双层样式）
     */
    private void drawHandle(GraphicsContext gc, double x, boolean isStart) {
        // 外圈
        gc.setFill(Color.WHITE);
        gc.fillOval(x - HANDLE_RADIUS, TRACK_Y - HANDLE_RADIUS + TRACK_HEIGHT / 2,
            HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);

        // 内圈
        gc.setFill(isStart ? Color.web("#2980b9") : Color.web("#E74C3C"));
        gc.fillOval(x - HANDLE_RADIUS + 3, TRACK_Y - HANDLE_RADIUS + 3 + TRACK_HEIGHT / 2,
            HANDLE_RADIUS * 2 - 6, HANDLE_RADIUS * 2 - 6);

        // 阴影
        gc.setStroke(Color.rgb(0, 0, 0, 0.2));
        gc.setLineWidth(1);
        gc.strokeOval(x - HANDLE_RADIUS, TRACK_Y - HANDLE_RADIUS + TRACK_HEIGHT / 2,
            HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
    }

    /**
     * 鼠标按下 - 检测拖动手柄
     */
    private void handleMousePressed(MouseEvent e) {
        double mx = e.getX();
        double my = e.getY();

        double startX = dateToX(startDate);
        double endX = dateToX(endDate);

        // 检查是否点击了手柄
        if (distance(mx, my, startX, TRACK_Y + TRACK_HEIGHT / 2) < HANDLE_RADIUS + 5) {
            currentDrag = DragTarget.START_HANDLE;
        } else if (distance(mx, my, endX, TRACK_Y + TRACK_HEIGHT / 2) < HANDLE_RADIUS + 5) {
            currentDrag = DragTarget.END_HANDLE;
        }
    }

    /**
     * 鼠标拖动 - 移动手柄
     */
    private void handleMouseDragged(MouseEvent e) {
        if (currentDrag == DragTarget.NONE) return;

        double mx = e.getX();
        double trackLeft = 20;
        double trackRight = trackLeft + sliderWidth;

        // 限制范围
        mx = Math.max(trackLeft, Math.min(trackRight, mx));

        LocalDate newDate = xToDate(mx);

        if (currentDrag == DragTarget.START_HANDLE) {
            // 起始不能晚于结束
            if (!newDate.isAfter(endDate)) {
                startDate = newDate;
                startDatePicker.setValue(startDate);
                startDatePicker.getEditor().setText(startDate.toString());
            }
        } else if (currentDrag == DragTarget.END_HANDLE) {
            // 结束不能早于起始
            if (!newDate.isBefore(startDate)) {
                endDate = newDate;
                endDatePicker.setValue(endDate);
                endDatePicker.getEditor().setText(endDate.toString());
            }
        }

        currentDateLabel.setText(formatDateRange(startDate, endDate));
        drawTimeline();

        // 通知监听器
        if (listener != null) {
            listener.onTimeRangeChanged(startDate, endDate);
        }
    }

    /**
     * 鼠标移动 - 检测悬停区域
     */
    private void handleMouseMoved(MouseEvent e) {
        double mx = e.getX();
        double my = e.getY();

        double startX = dateToX(startDate);
        double endX = dateToX(endDate);

        // 检测手柄悬停
        if (distance(mx, my, startX, TRACK_Y + TRACK_HEIGHT / 2) < HANDLE_RADIUS + 5 ||
            distance(mx, my, endX, TRACK_Y + TRACK_HEIGHT / 2) < HANDLE_RADIUS + 5) {
            timelineCanvas.setStyle("-fx-cursor: hand;");
        } else {
            timelineCanvas.setStyle("-fx-cursor: default;");
        }
    }

    /** 用户设定的起始日期（播放时起始不动） */
    private LocalDate fixedStartDate;
    /** 用户设定的结束日期（播放推进的上限） */
    private LocalDate fixedEndDate;

    /**
     * 切换播放/暂停
     */
    private void togglePlay() {
        isPlaying = !isPlaying;

        if (isPlaying) {
            playPauseBtn.setText("\u23F8 暂停");
            playPauseBtn.setStyle(
                "-fx-font-size: 11px; -fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-padding: 4 14; -fx-background-radius: 12; -fx-cursor: hand;"
            );

            // 记录用户当前设定的时间范围，播放时以此为边界
            fixedStartDate = startDate;
            fixedEndDate = endDate;

            lastPlayUpdate = 0;
            // 启动动画播放
            if (playTimer == null) {
                playTimer = new AnimationTimer() {
                    @Override
                    public void handle(long now) {
                        if (!isPlaying) {
                            playTimer.stop();
                            return;
                        }
                        if (lastPlayUpdate == 0) {
                            lastPlayUpdate = now;
                            return;
                        }

                        // 每 1.5 秒前进一次（速度 1x 时），速度越快间隔越短
                        long interval = 1_500_000_000L / playSpeed;
                        long elapsed = now - lastPlayUpdate;
                        if (elapsed >= interval) {
                            advanceTime();
                            lastPlayUpdate = now;
                        }
                    }
                };
            }
            playTimer.start();
        } else {
            playPauseBtn.setText("\u25B6 播放");
            playPauseBtn.setStyle(
                "-fx-font-size: 11px; -fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-padding: 4 14; -fx-background-radius: 12; -fx-cursor: hand;"
            );
            if (playTimer != null) {
                playTimer.stop();
            }
            lastPlayUpdate = 0;
        }

        if (listener != null) {
            listener.onPlayStateChanged(isPlaying);
        }
    }

    /**
     * 时间前进（播放模式）
     * <p>
     * 每次前进 30 天（乘以速度倍数），起始日期保持不变。
     * 到达用户设定的结束日期后自动暂停。
     * </p>
     */
    private void advanceTime() {
        long days = PLAY_STEP_DAYS * playSpeed;
        LocalDate newEnd = endDate.plusDays(days);

        // 不超过用户设定的结束日期上限
        if (newEnd.isAfter(fixedEndDate)) {
            newEnd = fixedEndDate;
        }

        endDate = newEnd;
        startDatePicker.setValue(startDate);
        startDatePicker.getEditor().setText(startDate.toString());
        endDatePicker.setValue(endDate);
        endDatePicker.getEditor().setText(endDate.toString());
        currentDateLabel.setText(formatDateRange(startDate, endDate));
        drawTimeline();

        if (listener != null) {
            listener.onTimeRangeChanged(startDate, endDate);
        }

        // 到达用户设定的结束日期，自动暂停
        if (endDate.equals(fixedEndDate)) {
            isPlaying = false;
            playPauseBtn.setText("\u25B6 播放");
            playPauseBtn.setStyle(
                "-fx-font-size: 11px; -fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-padding: 4 14; -fx-background-radius: 12; -fx-cursor: hand;"
            );
            if (playTimer != null) playTimer.stop();
            lastPlayUpdate = 0;
            if (listener != null) listener.onPlayStateChanged(false);
        }
    }

    /**
     * 重置时间范围为数据库全部数据范围
     */
    public void resetTimeRange() {
        startDate = minDate;
        endDate = maxDate;
        startDatePicker.setValue(startDate);
        startDatePicker.getEditor().setText(startDate.toString());
        endDatePicker.setValue(endDate);
        endDatePicker.getEditor().setText(endDate.toString());
        currentDateLabel.setText(formatDateRange(startDate, endDate));

        if (isPlaying) {
            togglePlay();
        }

        drawTimeline();

        if (listener != null) {
            listener.onTimeRangeChanged(startDate, endDate);
        }
    }

    /**
     * 设置监听器
     */
    public void setTimelineChangeListener(TimelineChangeListener listener) {
        this.listener = listener;
        // 如果数据库已就绪，立即通知当前范围
        if (dbReady && listener != null) {
            listener.onTimeRangeChanged(startDate, endDate);
        }
    }

    /**
     * 获取当前时间范围
     */
    public TimeRange getCurrentTimeRange() {
        return new TimeRange(startDate, endDate);
    }

    /**
     * 获取播放状态
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    // ==================== 坐标转换工具 ====================

    private double dateToX(LocalDate date) {
        long totalDays = maxDate.toEpochDay() - minDate.toEpochDay();
        long daysFromMin = date.toEpochDay() - minDate.toEpochDay();
        if (totalDays == 0) return 20;
        return 20 + (double) daysFromMin / totalDays * sliderWidth;
    }

    private LocalDate xToDate(double x) {
        long totalDays = maxDate.toEpochDay() - minDate.toEpochDay();
        double ratio = (x - 20) / sliderWidth;
        long daysOffset = (long) (ratio * totalDays);
        return minDate.plusDays(Math.max(0, Math.min(daysOffset, totalDays)));
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        return start.getYear() + "年" + start.getMonthValue() + "月 — " +
               end.getYear() + "年" + end.getMonthValue() + "月";
    }
}
