package com.bri.download;

import com.bri.config.DataSource;
import com.bri.config.CountryConfigLoader;
import com.bri.etl.GdeltImporter;
import com.bri.etl.ImportResult;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.net.ssl.SSLContext;

/**
 * GDELT 在线下载服务 — 高性能流水线版。
 *
 * <h3>核心优化</h3>
 * <ol>
 *   <li>下载/解压/导入流水线并行，互不阻塞</li>
 *   <li>IO 密集型下载线程数 = 16</li>
 *   <li>NIO 通道 + 256KB 直接缓冲区加速文件读写</li>
 *   <li>BRI 国家码全局缓存，避免每个文件重复查库</li>
 *   <li>进度回调节流，每 5 个文件报一次</li>
 * </ol>
 */
public class GdeltDownloadService {

    private static final String MASTER_URL = "https://data.gdeltproject.org/gdeltv2/masterfilelist.txt";
    private static final String CACHE_FILE = ".gdelt_master_cache.txt";
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;

    private static final int DOWNLOAD_THREADS = 32;
    private static final int IMPORT_THREADS = 6;
    private static final int MAX_RETRIES = 2;
    private static final int HTTP_TIMEOUT_MS = 10_000;
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final String GDELT_BASE_URL = "https://data.gdeltproject.org/gdeltv2/";

    private static final AtomicBoolean tlsInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean httpOptimized = new AtomicBoolean(false);

    private final DataSource dataSource;
    private final DownloadTracker tracker;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile GdeltImporter sharedImporter;
    private GdeltImporter.BriFilterMode briFilterMode = GdeltImporter.BriFilterMode.ANY;

    // ---- 内部数据结构 ----
    static class DownloadResult {
        final String fileName; final Path zipPath; final long fileSize; final String fileDate;
        DownloadResult(String f, Path z, long s, String d) { fileName=f; zipPath=z; fileSize=s; fileDate=d; }
    }

    // ---- 进度回调接口 ----
    public enum Phase { FETCHING_MASTER, DOWNLOADING, IMPORTING, BUILDING_INDEX, COMPLETE }

    public static class ProgressInfo {
        public Phase phase;
        public double percent;
        public int currentFileIndex;
        public int totalFiles;
        public String currentFileName;
        public String statusText;
        public long bytesDownloaded;
    }

    public interface ProgressCallback { void onProgress(ProgressInfo info); }

    public static class ScanResult {
        public int totalFiles, cachedFiles, missingFiles;
        public LocalDate minDate, maxDate;
    }

    // ---- 构造 ----
    public GdeltDownloadService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.tracker = new DownloadTracker(dataSource);
    }

    public void setBriFilterMode(GdeltImporter.BriFilterMode mode) {
        this.briFilterMode = (mode != null) ? mode : GdeltImporter.BriFilterMode.ANY;
    }

    // ---- 公共 API ----
    public ScanResult scan(LocalDate startDate, LocalDate endDate) {
        ScanResult result = new ScanResult();
        result.minDate = startDate;
        result.maxDate = endDate;
        List<String> allFiles = fetchMasterList(startDate, endDate);
        if (allFiles == null) allFiles = generateExpectedFiles(startDate, endDate);
        result.totalFiles = allFiles.size();
        Set<String> downloaded = tracker.filterDownloaded(new HashSet<>(allFiles));
        result.cachedFiles = downloaded.size();
        result.missingFiles = result.totalFiles - result.cachedFiles;
        return result;
    }

    /**
     * 高性能流水线下载。
     */
    public long downloadRange(LocalDate startDate, LocalDate endDate, ProgressCallback callback) throws Exception {
        cancelled.set(false);

        report(callback, Phase.FETCHING_MASTER, 0, 0, 0, "正在获取 GDELT 文件列表...");
        List<String> allFiles = fetchMasterList(startDate, endDate);
        if (allFiles == null) allFiles = generateExpectedFiles(startDate, endDate);
        report(callback, Phase.FETCHING_MASTER, 0.01, 0, allFiles.size(), "文件列表获取完成，共 " + allFiles.size() + " 个文件");

        Set<String> downloaded = tracker.filterDownloaded(new HashSet<>(allFiles));
        List<String> toDownload = new ArrayList<>();
        for (String f : allFiles) { if (!downloaded.contains(f)) toDownload.add(f); }

        if (toDownload.isEmpty()) {
            report(callback, Phase.COMPLETE, 1.0, 0, 0, "所有文件已缓存，无需下载");
            return 0;
        }

        int totalToDownload = toDownload.size();
        report(callback, Phase.DOWNLOADING, 0.02, 0, totalToDownload, "准备下载 " + totalToDownload + " 个文件...");

        try {
            dataSource.initIfAbsent();
            new CountryConfigLoader().loadToDatabase(dataSource);
        } catch (Exception e) {
            System.err.println("[GdeltDownload] 加载国家配置失败: " + e.getMessage());
        }

        Path tempDir = Files.createTempDirectory("gdelt_download_");
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicLong totalImported = new AtomicLong(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicInteger downloadedCount = new AtomicInteger(0);  // 下载完成（未含导入）的计数

        BlockingQueue<DownloadResult> downloadQueue = new LinkedBlockingQueue<>(500);
        DownloadResult POISON = new DownloadResult(null, null, 0, null);

        ExecutorService downloadExecutor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
        ExecutorService importExecutor = Executors.newFixedThreadPool(IMPORT_THREADS);

        try {
            Future<Long> importFuture = importExecutor.submit(() -> {
                long imported = 0;
                List<DownloadResult> batch = new ArrayList<>(200);
                while (true) {
                    DownloadResult dr = downloadQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (dr == POISON) break;
                    if (dr != null) {
                        batch.add(dr);
                        if (batch.size() >= 5) {
                            imported += processBatch(batch, callback, totalToDownload,
                                    completedCount, failedCount, totalImported);
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    imported += processBatch(batch, callback, totalToDownload,
                            completedCount, failedCount, totalImported);
                }
                return imported;
            });

            for (int i = 0; i < toDownload.size(); i++) {
                final String fileName = toDownload.get(i);
                downloadExecutor.submit(() -> {
                    if (cancelled.get()) return;
                    try {
                        String fileDate = extractDate(fileName);
                        String url = GDELT_BASE_URL + fileName;
                        Path zipPath = tempDir.resolve(fileName);
                        long fileSize = downloadFile(url, zipPath);
                        if (fileSize > 0) {
                            downloadQueue.put(new DownloadResult(fileName, zipPath, fileSize, fileDate));
                        } else {
                            tracker.markFailed(fileName, "文件为空");
                            failedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        tracker.markFailed(fileName, e.getMessage());
                        failedCount.incrementAndGet();
                    }
                    // 每完成一个下载就刷新进度（下载阶段 2%-30%）
                    int doneDL = downloadedCount.incrementAndGet();
                    double pct = 0.02 + (doneDL / (double) totalToDownload) * 0.28;
                    report(callback, Phase.DOWNLOADING, pct, doneDL, totalToDownload,
                            "下载中 (" + doneDL + "/" + totalToDownload + ")");
                });
            }

            downloadExecutor.shutdown();
            downloadExecutor.awaitTermination(30, TimeUnit.MINUTES);
            downloadQueue.put(POISON);
            long importedRows = importFuture.get(30, TimeUnit.MINUTES);
            importExecutor.shutdown();

            report(callback, Phase.BUILDING_INDEX, 0.95, completedCount.get(), totalToDownload, "正在构建分析索引...");
            report(callback, Phase.COMPLETE, 1.0, completedCount.get(), totalToDownload,
                    String.format("完成！导入 %d 条事件，失败 %d 个文件", importedRows, failedCount.get()));
            return importedRows;

        } finally {
            if (!downloadExecutor.isShutdown()) downloadExecutor.shutdownNow();
            if (!importExecutor.isShutdown()) importExecutor.shutdownNow();
            try { if (Files.isDirectory(tempDir)) Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete); }
            catch (IOException ignored) {}
        }
    }

    private long processBatch(List<DownloadResult> batch, ProgressCallback callback,
                              int totalToDownload, AtomicInteger completedCount,
                              AtomicInteger failedCount, AtomicLong totalImported) {
        long batchImported = 0;
        for (DownloadResult dr : batch) {
            if (cancelled.get()) break;
            try {
                Path csvPath = extractZipNio(dr.zipPath);
                if (csvPath == null) { tracker.markFailed(dr.fileName, "ZIP 内无 CSV"); failedCount.incrementAndGet(); continue; }
                ImportResult ir = getImporter().importFile(csvPath, dataSource, pct -> {}, briFilterMode);
                tracker.markDownloaded(dr.fileName, dr.fileDate, dr.fileSize, ir.getImportedRows());
                batchImported += ir.getImportedRows();
                totalImported.addAndGet(ir.getImportedRows());
                try { Files.deleteIfExists(csvPath); } catch (IOException ignored) {}
                try { Files.deleteIfExists(dr.zipPath); } catch (IOException ignored) {}
            } catch (Exception e) {
                tracker.markFailed(dr.fileName, e.getMessage());
                failedCount.incrementAndGet();
            }
            int done = completedCount.incrementAndGet();
            // 每个文件都刷新进度，不再累积（导入阶段 30%-90%）
            double pct = 0.30 + (done / (double) totalToDownload) * 0.60;
            report(callback, Phase.IMPORTING, pct, done, totalToDownload, "处理中 (" + done + "/" + totalToDownload + ")");
        }
        return batchImported;
    }

    public void cancel() { cancelled.set(true); }
    public DownloadTracker getTracker() { return tracker; }

    private GdeltImporter getImporter() {
        if (sharedImporter == null) {
            synchronized (this) { if (sharedImporter == null) sharedImporter = new GdeltImporter(); }
        }
        return sharedImporter;
    }

    // ---- 内部实现 ----

    private List<String> fetchMasterList(LocalDate startDate, LocalDate endDate) {
        Path cachePath = Path.of(CACHE_FILE);
        if (Files.exists(cachePath)) {
            try { long age = System.currentTimeMillis() - Files.getLastModifiedTime(cachePath).toMillis();
                if (age < CACHE_TTL_MS) return parseMasterList(cachePath, startDate, endDate);
            } catch (IOException ignored) {}
        }
        try {
            Path tmp = Path.of(CACHE_FILE + ".tmp");
            downloadFile(MASTER_URL, tmp);
            Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            return parseMasterList(cachePath, startDate, endDate);
        } catch (Exception e) {
            System.err.println("[GdeltDownload] 主列表失败: " + e.getMessage());
            if (Files.exists(cachePath)) return parseMasterList(cachePath, startDate, endDate);
            return null;
        }
    }

    private List<String> parseMasterList(Path p, LocalDate s, LocalDate e) {
        List<String> files = new ArrayList<>();
        String ss = s.format(DateTimeFormatter.BASIC_ISO_DATE);
        String es = e.format(DateTimeFormatter.BASIC_ISO_DATE);
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.contains("export.CSV.zip")) continue;
                int idx = line.lastIndexOf("http"); if (idx < 0) continue;
                String fn = line.substring(idx).substring(line.substring(idx).lastIndexOf('/') + 1);
                if (fn.length() < 8) continue;
                String dp = fn.substring(0, 8);
                if (dp.compareTo(ss) >= 0 && dp.compareTo(es) <= 0) files.add(fn);
            }
        } catch (IOException ex) { System.err.println("[GdeltDownload] 解析失败: " + ex.getMessage()); }
        return files;
    }

    private List<String> generateExpectedFiles(LocalDate s, LocalDate e) {
        List<String> files = new ArrayList<>();
        LocalDate cur = s;
        while (!cur.isAfter(e)) {
            String d = cur.format(DateTimeFormatter.BASIC_ISO_DATE);
            for (int h = 0; h < 24; h++)
                for (int m = 0; m < 60; m += 15)
                    files.add(String.format("%s%02d%02d00.export.CSV.zip", d, h, m));
            cur = cur.plusDays(1);
        }
        return files;
    }

    private static void ensureTls12() {
        if (tlsInitialized.compareAndSet(false, true)) {
            try { SSLContext ctx = SSLContext.getInstance("TLSv1.2"); ctx.init(null, null, null); SSLContext.setDefault(ctx); }
            catch (NoSuchAlgorithmException | KeyManagementException e) { System.err.println("[GdeltDownload] TLS 失败: " + e.getMessage()); }
        }
    }

    /** 启用 HTTP keep-alive 和连接复用，减少跨国网络延迟 */
    private static void ensureHttpOptimized() {
        if (httpOptimized.compareAndSet(false, true)) {
            System.setProperty("http.keepAlive", "true");
            System.setProperty("http.maxConnections", "64");
            System.setProperty("sun.net.http.errorstream.enableBuffering", "true");
            System.setProperty("sun.net.client.defaultConnectTimeout", String.valueOf(HTTP_TIMEOUT_MS));
            System.setProperty("sun.net.client.defaultReadTimeout", String.valueOf(HTTP_TIMEOUT_MS));
        }
    }

    /** NIO 通道高速下载 */
    private long downloadFile(String urlStr, Path destPath) throws IOException {
        ensureTls12();
        ensureHttpOptimized();
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(HTTP_TIMEOUT_MS);
                conn.setReadTimeout(HTTP_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "BRI-Analysis-System/2.0");
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setInstanceFollowRedirects(true);
                int status = conn.getResponseCode();
                if (status == 404) return 0;
                if (status >= 300 && status < 400) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (loc != null && !loc.isEmpty()) return downloadFile(loc, destPath);
                    throw new IOException("HTTP " + status);
                }
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                in = conn.getInputStream();
                try (ReadableByteChannel src = Channels.newChannel(in);
                     WritableByteChannel dst = Files.newByteChannel(destPath,
                             java.nio.file.StandardOpenOption.CREATE,
                             java.nio.file.StandardOpenOption.WRITE,
                             java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
                    long total = 0;
                    while (src.read(buf) != -1) { buf.flip(); total += dst.write(buf); buf.compact(); }
                    return total;
                }
            } catch (IOException e) {
                if (retry == MAX_RETRIES - 1) {
                    if (urlStr.startsWith("https://")) {
                        try { return downloadFile("http://" + urlStr.substring(8), destPath); } catch (IOException ignored) {}
                    }
                    throw e;
                }
                try { Thread.sleep(500L * (retry + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("interrupted", ie); }
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignored) {}
                if (conn != null) conn.disconnect();
            }
        }
        return 0;
    }

    /** NIO 零拷贝解压 */
    private Path extractZipNio(Path zipPath) throws IOException {
        Path parent = zipPath.getParent(); if (parent == null) parent = Path.of(".");
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath), BUFFER_SIZE))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.endsWith(".CSV") || name.endsWith(".csv")) {
                    Path outPath = parent.resolve(name.replaceAll(".*/", ""));
                    try (ReadableByteChannel src = Channels.newChannel(zis);
                         WritableByteChannel dst = Files.newByteChannel(outPath,
                                 java.nio.file.StandardOpenOption.CREATE,
                                 java.nio.file.StandardOpenOption.WRITE,
                                 java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
                        while (src.read(buf) != -1) { buf.flip(); dst.write(buf); buf.compact(); }
                    }
                    return outPath;
                }
            }
        }
        return null;
    }

    static String extractDate(String fileName) {
        if (fileName.length() >= 8) {
            try { return LocalDate.parse(fileName.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE).toString(); }
            catch (Exception ignored) {}
        }
        return LocalDate.now().toString();
    }

    private void report(ProgressCallback cb, Phase phase, double pct, int cur, int total, String status) {
        if (cb == null) return;
        ProgressInfo info = new ProgressInfo();
        info.phase = phase; info.percent = pct; info.currentFileIndex = cur;
        info.totalFiles = total; info.statusText = status;
        cb.onProgress(info);
    }
}
