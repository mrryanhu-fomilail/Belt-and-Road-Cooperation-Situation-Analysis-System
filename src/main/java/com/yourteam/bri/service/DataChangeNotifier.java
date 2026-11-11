package com.yourteam.bri.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据变更通知器 — 数据变更时广播事件通知所有已注册的面板。
 * <p>
 * 使用 {@link CopyOnWriteArrayList} 保证并发布安，支持多线程场景。
 * 各面板在构造时调用 {@link #addListener(DataChangeListener)} 注册自己。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class MyPanel extends VBox {
 *     public MyPanel() {
 *         DataChangeNotifier.addListener(() ->
 *             Platform.runLater(this::refreshData));
 *     }
 * }
 * }</pre>
 */
public final class DataChangeNotifier {

    private static final List<DataChangeListener> listeners = new CopyOnWriteArrayList<>();

    private DataChangeNotifier() {}

    /**
     * 注册数据变更监听器。
     *
     * @param listener 回调接口，收到通知后应更新 UI 数据
     */
    public static void addListener(DataChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器。
     *
     * @param listener 之前注册的监听器
     */
    public static void removeListener(DataChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器 — 数据已变更，请刷新。
     * <p>
     * 调用者通常在后台线程执行完成后调用此方法。
     * 监听器内部应使用 {@code Platform.runLater()} 回到 UI 线程更新界面。
     * </p>
     */
    public static void notifyDataChanged() {
        for (DataChangeListener listener : listeners) {
            try {
                listener.onDataChanged();
            } catch (Exception e) {
                System.err.println("[DataChangeNotifier] 通知失败: " + e.getMessage());
            }
        }
    }
}
