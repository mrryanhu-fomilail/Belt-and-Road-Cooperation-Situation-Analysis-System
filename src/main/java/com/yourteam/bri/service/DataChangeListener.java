package com.yourteam.bri.service;

/**
 * 数据变更监听器 — 当新数据导入加载完成后通知 GUI 面板刷新。
 * <p>
 * 面板通过 {@link DataChangeNotifier#addListener(DataChangeListener)} 注册，
 * 当 {@link BriEngineHolder#loadFile(String)} 成功执行后自动触发回调。
 * </p>
 *
 * @see DataChangeNotifier 数据变更通知器
 * @see BriEngineHolder BRI 引擎持有者
 */
@FunctionalInterface
public interface DataChangeListener {

    /**
     * 数据已变更（新数据加载完成），面板应重新读取并刷新界面。
     */
    void onDataChanged();
}
