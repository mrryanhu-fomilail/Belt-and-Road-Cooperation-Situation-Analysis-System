package com.bri.etl;

/**
 * 导入进度回调接口 — 供 UI 层（JavaFX 进度条）监听导入进度。
 *
 * <p>生产者/消费者线程在关键节点调用 {@link #onProgress(double)} 报告进度，
 * UI 线程通过 {@code Platform.runLater} 安全更新进度条。
 * 导入结束时调用 {@link #onCompleted(ImportResult)} 传递统计结果。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * GdeltImporter importer = new GdeltImporter();
 * importer.importFile(tsvPath, ds, new ProgressListener() {
 *     public void onProgress(double percent) {
 *         Platform.runLater(() -> progressBar.setProgress(percent));
 *     }
 *     public void onCompleted(ImportResult result) {
 *         Platform.runLater(() -> showResult(result));
 *     }
 * });
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see GdeltImporter
 * @see ImportResult
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * 进度更新回调。
     *
     * @param percent 完成百分比，范围 0.0 ~ 1.0
     */
    void onProgress(double percent);

    /**
     * 导入完成回调（默认空实现）。
     *
     * @param result 导入统计结果，含成功/失败/跳过计数
     */
    default void onCompleted(ImportResult result) {
        // 默认不做处理，子类可覆盖
    }

    /**
     * 导入过程中发生警告时的回调（默认空实现）。
     *
     * @param message 警告信息
     */
    default void onWarning(String message) {
        // 默认不做处理，子类可覆盖
    }

    /**
     * 导入过程中发生错误时的回调（默认空实现）。
     * <p>注意：解析级错误通常跳过行并计数，不会触发此回调。
     * 此回调用于 I/O 错误、数据库错误等致命问题。</p>
     *
     * @param message 错误信息
     * @param cause   原始异常
     */
    default void onError(String message, Throwable cause) {
        // 默认不做处理，子类可覆盖
    }
}
