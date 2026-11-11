package com.bri;

import com.bri.config.CountryConfigLoader;
import com.bri.config.DataSource;
import com.bri.etl.GdeltImporter;
import com.bri.etl.ImportResult;

import java.nio.file.Path;

/**
 * 批量导入命令行工具 — 指定 GDELT 数据目录，一键全部导入。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * java -cp ... com.bri.BulkImport ./gdelt_data/2025-06/
 * java -cp ... com.bri.BulkImport ./gdelt_data/2025-06/ my_bri.db
 * }</pre>
 *
 * <p>导入前自动建库并加载沿线国家列表（幂等，数据不丢失）。</p>
 */
public class BulkImport {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("用法: BulkImport <数据目录> [数据库路径]");
            System.out.println("示例: BulkImport ./gdelt_data/2025-06/");
            System.out.println("      BulkImport ./gdelt_data/2025-06/ my_bri.db");
            System.exit(1);
        }

        Path dir = Path.of(args[0]);
        String dbPath = args.length >= 2 ? args[1] : "bri.db";

        System.out.println("数据目录: " + dir.toAbsolutePath());
        System.out.println("数据库:   " + dbPath);
        System.out.println();

        // 1. 初始化
        DataSource ds = DataSource.getInstance(dbPath);
        ds.initIfAbsent();

        // 2. 加载沿线国家（幂等）
        int countryCount = new CountryConfigLoader().loadToDatabase(ds);
        System.out.println("沿线国家: " + countryCount + " 个");

        // 3. 批量导入
        System.out.println("开始导入...");
        long start = System.currentTimeMillis();

        ImportResult result = new GdeltImporter().importDirectory(
                dir, ds,
                pct -> System.out.printf("\r  进度: %.0f%%", pct * 100)
        );

        long elapsed = System.currentTimeMillis() - start;
        System.out.println();

        // 4. 结果
        System.out.println();
        System.out.println("========== 导入完成 ==========");
        System.out.println("文件扫描: " + result.getTotalRows() + " 行");
        System.out.println("实际入库: " + result.getImportedRows() + " 条");
        System.out.println("跳过:     " + result.getSkippedRows() + " 条");
        System.out.println("过滤:     " + result.getFilteredOut() + " 条（非沿线国家）");
        System.out.println("耗时:     " + String.format("%.1f 秒", elapsed / 1000.0));
        System.out.println("速率:     " + String.format("%.0f 条/秒", result.getRowsPerSecond()));

        ds.close();
    }
}
