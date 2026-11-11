package com.bri.download;

import com.bri.config.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 已下载文件追踪器 — 管理 downloaded_files 表，实现增量下载去重。
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>查询某日期范围内已下载的文件集合（用于去重）</li>
 *   <li>标记文件已下载（导入完成后调用）</li>
 *   <li>获取已下载数据的日期范围（用于 UI 展示）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DownloadTracker tracker = new DownloadTracker(dataSource);
 * Set<String> downloaded = tracker.getDownloadedFiles(startDate, endDate);
 * // ... 下载并导入新文件 ...
 * tracker.markDownloaded("20250601000000.export.CSV.zip", "2025-06-01", 15000);
 * }</pre>
 */
public class DownloadTracker {

    private final DataSource dataSource;

    public DownloadTracker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 获取指定日期范围内已下载的文件名集合。
     *
     * @param from 开始日期（含）
     * @param to   结束日期（含）
     * @return 已下载的文件名集合（仅含 export.CSV.zip）
     */
    public Set<String> getDownloadedFiles(LocalDate from, LocalDate to) {
        Set<String> files = new HashSet<>();
        String sql = "SELECT file_name FROM downloaded_files "
                + "WHERE file_date >= ? AND file_date <= ? AND status = 'completed'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(rs.getString("file_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 查询已下载文件失败: " + e.getMessage());
        }
        return files;
    }

    /**
     * 批量获取指定日期范围内已下载的文件名集合（使用 IN 查询优化）。
     *
     * @param fileNames 待检查的文件名列表
     * @return 其中已下载的文件名集合
     */
    public Set<String> filterDownloaded(Set<String> fileNames) {
        Set<String> downloaded = new HashSet<>();
        if (fileNames == null || fileNames.isEmpty()) return downloaded;

        // 分批查询，避免 SQL 过长
        String[] names = fileNames.toArray(new String[0]);
        int batchSize = 500;
        String sql = "SELECT file_name FROM downloaded_files WHERE file_name IN (";

        try (Connection conn = dataSource.getConnection()) {
            for (int i = 0; i < names.length; i += batchSize) {
                int end = Math.min(i + batchSize, names.length);
                StringBuilder sb = new StringBuilder(sql);
                for (int j = i; j < end; j++) {
                    sb.append(j > i ? ",?" : "?");
                }
                sb.append(")");

                try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                    for (int j = i; j < end; j++) {
                        ps.setString(j - i + 1, names[j]);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            downloaded.add(rs.getString("file_name"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 批量过滤失败: " + e.getMessage());
        }
        return downloaded;
    }

    /**
     * 标记一个文件已成功下载并导入。
     *
     * @param fileName    GDELT 文件名（如 20250601000000.export.CSV.zip）
     * @param fileDate    文件日期（yyyy-MM-dd）
     * @param fileSize    文件大小（字节），可为 0
     * @param recordCount 导入的记录数
     */
    public void markDownloaded(String fileName, String fileDate, long fileSize, long recordCount) {
        String sql = "INSERT OR REPLACE INTO downloaded_files "
                + "(file_name, file_date, download_time, file_size, record_count, status) "
                + "VALUES (?, ?, datetime('now'), ?, ?, 'completed')";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ps.setString(2, fileDate);
            ps.setLong(3, fileSize);
            ps.setLong(4, recordCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 标记下载失败: " + fileName + " - " + e.getMessage());
        }
    }

    /**
     * 标记文件下载/导入失败。
     *
     * @param fileName GDELT 文件名
     * @param reason   失败原因
     */
    public void markFailed(String fileName, String reason) {
        String sql = "INSERT OR REPLACE INTO downloaded_files "
                + "(file_name, file_date, download_time, file_size, record_count, status) "
                + "VALUES (?, '', datetime('now'), 0, 0, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ps.setString(2, "failed: " + truncate(reason, 200));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 标记失败: " + fileName + " - " + e.getMessage());
        }
    }

    /**
     * 获取数据库中已有数据的最早和最晚日期。
     *
     * @return [最早日期, 最晚日期]，无数据时返回 null
     */
    public LocalDate[] getDataDateRange() {
        String sql = "SELECT MIN(file_date) AS min_date, MAX(file_date) AS max_date "
                + "FROM downloaded_files WHERE status = 'completed'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString("min_date") != null) {
                return new LocalDate[]{
                    LocalDate.parse(rs.getString("min_date")),
                    LocalDate.parse(rs.getString("max_date"))
                };
            }
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 查询日期范围失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取已下载文件总数。
     */
    public int getDownloadedCount() {
        String sql = "SELECT COUNT(*) FROM downloaded_files WHERE status = 'completed'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 统计失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 获取已导入事件总数。
     */
    public int getTotalRecordCount() {
        String sql = "SELECT COALESCE(SUM(record_count), 0) FROM downloaded_files WHERE status = 'completed'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DownloadTracker] 统计事件失败: " + e.getMessage());
        }
        return 0;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
