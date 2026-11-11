package com.bri.etl;

import com.bri.config.DataSource;
import com.bri.model.Event;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.bri.etl.GdeltParser.isActorEmpty;

/**
 * GDELT 数据导入管线程 — 多线程生产者-消费者模式的 ETL 入口（F-01~F-03）。
 *
 * <h2>管线流程</h2>
 * <ol>
 *   <li><b>预加载</b>：从 countries 表加载沿线国家 CAMEO 码到内存 Set</li>
 *   <li><b>生产者线程</b>：用 OpenCSV 流式读取 TSV → {@link GdeltParser} 逐行解析 →
 *       过滤非沿线国家 → 丢弃空 actor → 放入 {@link BlockingQueue}</li>
 *   <li><b>消费者线程</b>：从队列取 Event → 批量累积 1000 条 →
 *       {@code executeBatch} 写入 → 每批 COMMIT</li>
 *   <li><b>进度回调</b>：生产者每处理一批行就通过 {@link ProgressListener} 通知进度</li>
 * </ol>
 *
 * <h2>性能目标（NF-01）</h2>
 * <p>20 万条数据导入不超过 2 分钟（目标 &lt; 120 秒）。
 * 通过 WAL 模式 + NORMAL synchronous + 批量事务 + 预编译语句实现。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * GdeltImporter importer = new GdeltImporter();
 * ImportResult result = importer.importFile(
 *     Path.of("20250601.export.CSV"),
 *     DataSource.getInstance("bri.db"),
 *     percent -> progressBar.setProgress(percent)  // UI 进度条
 * );
 * System.out.println(result);
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see GdeltParser
 * @see ProgressListener
 * @see ImportResult
 */
public class GdeltImporter {

    /**
     * BRI 国家过滤模式 — 控制导入时按一带一路国家过滤的粒度。
     */
    public enum BriFilterMode {
        /** 双边均为一带一路国家（actor1 和 actor2 都在 BRI 列表中） */
        BOTH,
        /** 至少一方为一带一路国家（默认，兼容旧行为） */
        ANY,
        /** 不过滤，导入全部事件 */
        NONE
    }


    /** 批量提交大小 */
    private static final int BATCH_SIZE = 1000;

    /** 队列容量（防止生产者内存溢出） */
    private static final int QUEUE_CAPACITY = 10_000;

    /** 进度通知间隔（每处理 N 行通知一次，避免回调过于频繁） */
    private static final int PROGRESS_INTERVAL = 500;

    /** INSERT SQL */
    private static final String INSERT_SQL =
            "INSERT OR IGNORE INTO events"
                    + "(event_id, event_date, actor1_code, actor2_code,"
                    + " event_root, event_code, is_coop, goldstein,"
                    + " avg_tone, num_mentions, lat, lon, source_url)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final Logger LOG = LoggerFactory.getLogger(GdeltImporter.class);

    private final GdeltParser parser;

    // ==================== 构造 ====================

    /**
     * 创建导入器实例。
     */
    public GdeltImporter() {
        this.parser = new GdeltParser();
    }

    // ==================== 公共入口 ====================

    /**
     * 导入单个 GDELT TSV 文件（F-01 核心入口）。
     *
     * <p>线程安全：可在后台线程调用以保持 UI 响应。</p>
     *
     * @param tsvPath   GDELT 2.0 export.CSV 文件路径（实际 TSV 格式）
     * @param ds        数据库数据源
     * @param listener  进度回调（可为 null 表示不需要进度通知）
     * @return 导入统计结果
     * @throws IllegalArgumentException 如果文件不存在或数据库未初始化
     * @throws IllegalStateException    如果数据库操作失败
     */
    public ImportResult importFile(Path tsvPath, DataSource ds,
                                   ProgressListener listener) {
        return importFile(tsvPath, ds, listener, BriFilterMode.ANY);
    }

    /**
     * 导入单个 GDELT TSV 文件（指定 BRI 过滤模式）。
     *
     * @param tsvPath    GDELT 2.0 export.CSV 文件路径
     * @param ds         数据库数据源
     * @param listener   进度回调
     * @param filterMode BRI 过滤模式
     * @return 导入统计结果
     */
    public ImportResult importFile(Path tsvPath, DataSource ds,
                                   ProgressListener listener,
                                   BriFilterMode filterMode) {
        // 前置校验
        if (!Files.isRegularFile(tsvPath)) {
            throw new IllegalArgumentException("文件不存在: " + tsvPath);
        }
        if (!ds.isSchemaReady()) {
            throw new IllegalArgumentException("数据库 schema 未初始化，请先调用 ds.initIfAbsent()");
        }

        long startTime = System.currentTimeMillis();

        try {
            LOG.info("开始导入: {} ({} bytes)", tsvPath, Files.size(tsvPath));
        } catch (IOException ignored) {
            LOG.info("开始导入: {}", tsvPath);
        }

        // 1. 预加载沿线国家码
        Set<String> briCodes;
        try {
            briCodes = loadBriCodes(ds);
        } catch (SQLException e) {
            throw new IllegalStateException("加载沿线国家列表失败", e);
        }
        if (briCodes.isEmpty()) {
            LOG.warn("沿线国家列表为空——所有非中国事件将被过滤！请先调用 CountryConfigLoader.loadToDatabase()");
            fireWarning(listener, "沿线国家列表为空，本次导入将过滤掉所有记录！"
                    + "请先调用 CountryConfigLoader.loadToDatabase()。");
        }

        // 2. 统计文件总行数（用于进度百分比）
        long totalLines = countLines(tsvPath);

        // 3. 多线程管线
        ImportResult.Builder result = new ImportResult.Builder()
                .totalRows(totalLines);

        BlockingQueue<Event> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 共享计数器
        AtomicLong parsedCount = new AtomicLong(0);
        AtomicLong skippedCount = new AtomicLong(0);
        AtomicLong filteredCount = new AtomicLong(0);
        AtomicLong emptyActorCount = new AtomicLong(0);
        AtomicLong geoCleanedCount = new AtomicLong(0);
        AtomicLong importedCount = new AtomicLong(0);
        AtomicInteger lastProgress = new AtomicInteger(0);

        GdeltParser.SkipCounter skipper = new GdeltParser.SkipCounter();

        // 毒丸标记（使用特殊 eventId 标识，不依赖 equals）
        final Event POISON = new Event();
        POISON.setEventId(Long.MIN_VALUE);

        // ---- 生产者 ----
        Future<?> producer = executor.submit(() -> {
            try {
                CSVParser csvParser = new CSVParserBuilder()
                        .withSeparator('\t')
                        .withQuoteChar('"')
                        .build();
                try (CSVReader reader = new CSVReaderBuilder(
                        new InputStreamReader(
                                new FileInputStream(tsvPath.toFile()),
                                StandardCharsets.UTF_8))
                        .withCSVParser(csvParser)
                        .build()) {
                    String[] row;
                    while ((row = reader.readNext()) != null) {
                        parsedCount.incrementAndGet();

                        // 解析
                        Event e = parser.parse(row, skipper);
                        if (e == null) {
                            skippedCount.incrementAndGet();
                            continue;
                        }

                        // 清洗 F-03: 空 actor 丢弃
                        if (isActorEmpty(e.getActor1Code())
                                && isActorEmpty(e.getActor2Code())) {
                            emptyActorCount.incrementAndGet();
                            skipper.emptyActor++;
                            continue;
                        }

                        // 过滤 F-01: 按 BRI 过滤模式筛选沿线国家
                        String a1 = e.getActor1Code();
                        String a2 = e.getActor2Code();
                        if (filterMode != BriFilterMode.NONE) {
                            boolean a1Bri = briCodes.contains(a1);
                            boolean a2Bri = briCodes.contains(a2);
                            if (filterMode == BriFilterMode.BOTH) {
                                // 双边均为一带一路国家
                                if (!a1Bri || !a2Bri) {
                                    filteredCount.incrementAndGet();
                                    continue;
                                }
                            } else {
                                // ANY: 至少一方属于沿线国家
                                if (!a1Bri && !a2Bri) {
                                    filteredCount.incrementAndGet();
                                    continue;
                                }
                            }
                        }

                        // 放入队列（阻塞直到有空间）
                        queue.put(e);

                        // 进度通知
                        long parsed = parsedCount.get();
                        if (parsed % PROGRESS_INTERVAL == 0 && totalLines > 0) {
                            int pct = (int) (parsed * 100 / totalLines);
                            if (pct > lastProgress.get()) {
                                lastProgress.set(pct);
                                fireProgress(listener, pct / 100.0);
                            }
                        }
                    }
                }
            } catch (IOException | CsvValidationException e) {
                LOG.error("TSV 文件读取/解析失败: {}", tsvPath, e);
                fireError(listener, "TSV 文件读取失败", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fireWarning(listener, "生产者线程被中断");
            } finally {
                // 发送毒丸通知消费者退出
                try {
                    queue.put(POISON);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // ---- 消费者 ----
        Future<?> consumer = executor.submit(() -> {
            List<Event> batch = new ArrayList<>(BATCH_SIZE);
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    while (true) {
                        Event e = queue.poll(500, TimeUnit.MILLISECONDS);
                        if (e == POISON || (e != null && e.getEventId() == Long.MIN_VALUE)) {
                            break; // 生产者完成
                        }
                        if (e != null) {
                            batch.add(e);
                            if (batch.size() >= BATCH_SIZE) {
                                executeBatch(ps, batch);
                                importedCount.addAndGet(batch.size());
                                conn.commit();
                                batch.clear();
                            }
                        }
                    }
                    // 处理剩余批次
                    if (!batch.isEmpty()) {
                        executeBatch(ps, batch);
                        importedCount.addAndGet(batch.size());
                        conn.commit();
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    LOG.error("数据库写入失败，批次已回滚（可能是磁盘满、权限不足）", e);
                    fireError(listener, "数据库写入失败，已回滚", e);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                fireError(listener, "消费者线程异常", e);
            }
        });

        // 等待完成
        try {
            producer.get(5, TimeUnit.MINUTES);
            consumer.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            fireError(listener, "导入超时（超过 5 分钟），可能性能不达标", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            fireError(listener, "导入线程异常", e.getCause());
        } finally {
            executor.shutdownNow();
        }

        // 最终进度
        fireProgress(listener, 1.0);

        // 构建结果
        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("导入完成: 耗时={}ms, 入率为={}行/秒",
                elapsed, String.format("%.0f", importedCount.get() * 1000.0 / Math.max(elapsed, 1)));
        ImportResult importResult = result
                .parsedRows(parsedCount.get() - skippedCount.get())
                .skippedRows(skippedCount.get())
                .filteredOut(filteredCount.get())
                .emptyActorDiscarded(emptyActorCount.get())
                .geoCleaned(skipper.geoCleaned + geoCleanedCount.get())
                .importedRows(importedCount.get())
                .elapsedMs(elapsed)
                .build();

        fireCompleted(listener, importResult);
        return importResult;
    }

    /**
     * 批量导入目录下所有 GDELT TSV/CSV 文件。
     *
     * <p>自动扫描目录中的 {@code *.export.CSV}、{@code *.CSV}、{@code *.tsv}
     * 文件，按文件名排序后逐个导入。所有文件的导入结果汇总为一个
     * {@link ImportResult} 返回。</p>
     *
     * <p>BRI 国家码只加载一次，所有文件共享同一过滤规则，
     * 避免每个文件都查一次 countries 表。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * GdeltImporter importer = new GdeltImporter();
     * // 把 2025 年 6 月的所有 GDELT 日数据放进一个目录
     * ImportResult total = importer.importDirectory(
     *     Path.of("./gdelt_data/2025-06/"),
     *     DataSource.getInstance("bri.db"),
     *     (file, pct) -> System.out.printf("%s: %.0f%%\n", file, pct * 100)
     * );
     * System.out.println("总计入库: " + total.getImportedRows() + " 条");
     * }</pre>
     *
     * @param dir       包含 GDELT TSV 文件的目录
     * @param ds        数据源
     * @param listener  进度回调（接收当前文件名和进度百分比）
     * @return 所有文件导入结果的汇总
     * @throws IllegalArgumentException 如果目录不存在或数据库未初始化
     */
    public ImportResult importDirectory(Path dir, DataSource ds,
                                        ProgressListener listener) {
        return importDirectory(dir, ds, listener, BriFilterMode.ANY);
    }

    /**
     * 批量导入目录（指定 BRI 过滤模式）。
     *
     * @param dir        包含 GDELT TSV 文件的目录
     * @param ds         数据源
     * @param listener   进度回调
     * @param filterMode BRI 过滤模式
     * @return 所有文件导入结果的汇总
     */
    public ImportResult importDirectory(Path dir, DataSource ds,
                                        ProgressListener listener,
                                        BriFilterMode filterMode) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dir);
        }
        if (!ds.isSchemaReady()) {
            throw new IllegalArgumentException(
                    "数据库 schema 未初始化，请先调用 ds.initIfAbsent()");
        }

        // 扫描 TSV/CSV 文件
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".csv")
                                || name.endsWith(".tsv")
                                || name.endsWith(".txt");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("目录扫描失败: " + dir, e);
        }

        if (files.isEmpty()) {
            LOG.warn("目录中未找到 CSV/TSV 文件: {}", dir);
            return new ImportResult.Builder()
                    .totalRows(0).parsedRows(0).skippedRows(0)
                    .filteredOut(0).emptyActorDiscarded(0).geoCleaned(0)
                    .importedRows(0).elapsedMs(0).build();
        }

        long overallStart = System.currentTimeMillis();
        LOG.info("批量导入开始: {} 个文件 in {}", files.size(), dir);

        // 使用 BatchProgressListener 包装用户回调，汇总文件级进度到总体进度
        BatchProgressListener batchListener = new BatchProgressListener(
                files.size(), listener);

        long totalRows = 0, parsedRows = 0, skippedRows = 0;
        long filteredOut = 0, emptyDiscarded = 0, geoCleaned = 0, importedRows = 0;

        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            batchListener.onFileStart(f, i);

            ImportResult r = importFile(f, ds, batchListener, filterMode);

            totalRows += r.getTotalRows();
            parsedRows += r.getParsedRows();
            skippedRows += r.getSkippedRows();
            filteredOut += r.getFilteredOut();
            emptyDiscarded += r.getEmptyActorDiscarded();
            geoCleaned += r.getGeoCleaned();
            importedRows += r.getImportedRows();

            batchListener.onFileEnd(f, i, r);
        }

        long overallElapsed = System.currentTimeMillis() - overallStart;
        LOG.info("批量导入完成: {} 文件, 总计 {} 行, 耗时 {}ms",
                files.size(), importedRows, overallElapsed);

        return new ImportResult.Builder()
                .totalRows(totalRows)
                .parsedRows(parsedRows)
                .skippedRows(skippedRows)
                .filteredOut(filteredOut)
                .emptyActorDiscarded(emptyDiscarded)
                .geoCleaned(geoCleaned)
                .importedRows(importedRows)
                .elapsedMs(overallElapsed)
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 从 countries 表加载沿线国家 CAMEO 码到内存 Set。
     */
    private Set<String> loadBriCodes(DataSource ds) throws SQLException {
        Set<String> codes = new HashSet<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT cameo_code FROM countries WHERE is_bri = 1")) {
            while (rs.next()) {
                codes.add(rs.getString("cameo_code"));
            }
        }
        return codes;
    }

    /**
     * 快速统计文件行数。
     */
    private long countLines(Path path) {
        try (var lines = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            long count = 0;
            while (lines.readLine() != null) count++;
            return count;
        } catch (IOException e) {
            return -1; // 无法统计，进度条将不做百分比通知
        }
    }

    /**
     * 批量执行 INSERT。
     */
    private void executeBatch(PreparedStatement ps, List<Event> batch)
            throws SQLException {
        for (Event e : batch) {
            ps.setLong(1, e.getEventId());
            ps.setString(2, e.getEventDate());
            ps.setString(3, e.getActor1Code());
            ps.setString(4, e.getActor2Code());
            ps.setString(5, e.getEventRoot());
            ps.setString(6, e.getEventCode());
            ps.setInt(7, e.getIsCoop());
            ps.setDouble(8, e.getGoldstein());
            ps.setDouble(9, e.getAvgTone());
            ps.setInt(10, e.getNumMentions());
            if (e.getLat() != 0.0 || e.getLon() != 0.0) {
                ps.setDouble(11, e.getLat());
                ps.setDouble(12, e.getLon());
            } else {
                ps.setNull(11, java.sql.Types.REAL);
                ps.setNull(12, java.sql.Types.REAL);
            }
            // source_url
            String url = e.getSourceUrl();
            if (url != null && !url.isEmpty()) {
                ps.setString(13, url);
            } else {
                ps.setNull(13, java.sql.Types.VARCHAR);
            }
            ps.addBatch();
        }
        ps.executeBatch();
    }

    // ==================== 批量导入进度适配器 ====================

    /**
     * 批量导入进度适配器 — 将每个文件的进度（0~1）映射到整体进度。
     *
     * <p>同时代理给用户的 {@link ProgressListener}，
     * 让用户感知当前正在导入哪个文件及整体完成百分比。</p>
     */
    private static class BatchProgressListener implements ProgressListener {
        private final int totalFiles;
        private final ProgressListener delegate;
        private int currentFileIndex;

        BatchProgressListener(int totalFiles, ProgressListener delegate) {
            this.totalFiles = totalFiles;
            this.delegate = delegate;
        }

        void onFileStart(Path file, int index) {
            this.currentFileIndex = index;
            fire(delegate, 0.0);
        }

        void onFileEnd(Path file, int index, ImportResult r) {
            double overall = (index + 1.0) / totalFiles;
            fire(delegate, overall);
        }

        @Override
        public void onProgress(double filePercent) {
            // 将文件内进度映射到整体进度
            double overall = (currentFileIndex + filePercent) / totalFiles;
            fire(delegate, overall);
        }

        private void fire(ProgressListener l, double pct) {
            if (l != null) {
                try { l.onProgress(pct); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 回调辅助 ====================

    private void fireProgress(ProgressListener l, double pct) {
        if (l != null) {
            try { l.onProgress(pct); } catch (Exception ignored) {}
        }
    }

    private void fireCompleted(ProgressListener l, ImportResult r) {
        if (l != null) {
            try { l.onCompleted(r); } catch (Exception ignored) {}
        }
    }

    private void fireWarning(ProgressListener l, String msg) {
        if (l != null) {
            try { l.onWarning(msg); } catch (Exception ignored) {}
        }
    }

    private void fireError(ProgressListener l, String msg, Throwable cause) {
        if (l != null) {
            try { l.onError(msg, cause); } catch (Exception ignored) {}
        }
    }
}
