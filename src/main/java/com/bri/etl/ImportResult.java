package com.bri.etl;

/**
 * 导入结果统计 — 记录一次 GDELT 数据导入的完整统计信息。
 *
 * <p>包含解析、过滤、清洗、写入各阶段的计数，以及耗时和最终成功率。
 * 由 {@link GdeltImporter} 在导入完成后构建并返回。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see GdeltImporter
 * @see ProgressListener#onCompleted(ImportResult)
 */
public final class ImportResult {

    /** TSV 文件总行数 */
    private final long totalRows;

    /** 成功解析的行数 */
    private final long parsedRows;

    /** 因列数不足/数字解析失败跳过的行数 */
    private final long skippedRows;

    /** 非一带一路沿线国家相关事件（已过滤） */
    private final long filteredOut;

    /** 因空 actor 丢弃的行数 */
    private final long emptyActorDiscarded;

    /** 坐标越界置 null 的事件数 */
    private final long geoCleaned;

    /** 实际写入数据库的行数 */
    private final long importedRows;

    /** 导入耗时（毫秒） */
    private final long elapsedMs;

    // ==================== 构造 ====================

    private ImportResult(Builder builder) {
        this.totalRows = builder.totalRows;
        this.parsedRows = builder.parsedRows;
        this.skippedRows = builder.skippedRows;
        this.filteredOut = builder.filteredOut;
        this.emptyActorDiscarded = builder.emptyActorDiscarded;
        this.geoCleaned = builder.geoCleaned;
        this.importedRows = builder.importedRows;
        this.elapsedMs = builder.elapsedMs;
    }

    // ==================== Getters ====================

    /** @return 文件总行数 */
    public long getTotalRows() { return totalRows; }

    /** @return 成功解析的行数 */
    public long getParsedRows() { return parsedRows; }

    /** @return 因格式问题跳过的行数 */
    public long getSkippedRows() { return skippedRows; }

    /** @return 非沿线国家事件被过滤数 */
    public long getFilteredOut() { return filteredOut; }

    /** @return 空 actor 丢弃数 */
    public long getEmptyActorDiscarded() { return emptyActorDiscarded; }

    /** @return 坐标清洗数 */
    public long getGeoCleaned() { return geoCleaned; }

    /** @return 实际入库行数 */
    public long getImportedRows() { return importedRows; }

    /** @return 耗时（毫秒） */
    public long getElapsedMs() { return elapsedMs; }

    /**
     * 计算导入成功率。
     *
     * @return 入库行数 / 总行数，若总行数为 0 返回 0
     */
    public double getSuccessRate() {
        return totalRows == 0 ? 0.0 : (double) importedRows / totalRows;
    }

    /**
     * 计算每秒导入行数。
     *
     * @return 吞吐率（行/秒），耗时 ≤ 0 时返回 0
     */
    public double getRowsPerSecond() {
        return elapsedMs <= 0 ? 0.0
                : (double) importedRows / elapsedMs * 1000.0;
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return String.format(
                "ImportResult{总行=%d, 解析=%d, 跳过=%d, 过滤=%d, "
                        + "空Actor丢弃=%d, 坐标清洗=%d, 入库=%d, "
                        + "耗时=%.1fs, 速率=%.0f行/s, 成功率=%.1f%%}",
                totalRows, parsedRows, skippedRows, filteredOut,
                emptyActorDiscarded, geoCleaned, importedRows,
                elapsedMs / 1000.0, getRowsPerSecond(),
                getSuccessRate() * 100);
    }

    // ==================== Builder ====================

    /**
     * ImportResult 建造器。
     */
    public static class Builder {
        private long totalRows;
        private long parsedRows;
        private long skippedRows;
        private long filteredOut;
        private long emptyActorDiscarded;
        private long geoCleaned;
        private long importedRows;
        private long elapsedMs;

        public Builder totalRows(long v) { this.totalRows = v; return this; }
        public Builder parsedRows(long v) { this.parsedRows = v; return this; }
        public Builder skippedRows(long v) { this.skippedRows = v; return this; }
        public Builder filteredOut(long v) { this.filteredOut = v; return this; }
        public Builder emptyActorDiscarded(long v) { this.emptyActorDiscarded = v; return this; }
        public Builder geoCleaned(long v) { this.geoCleaned = v; return this; }
        public Builder importedRows(long v) { this.importedRows = v; return this; }
        public Builder elapsedMs(long v) { this.elapsedMs = v; return this; }

        /**
         * 构建 ImportResult 实例。
         *
         * @return 不可变的统计结果
         */
        public ImportResult build() {
            return new ImportResult(this);
        }
    }
}
