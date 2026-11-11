package com.bri;

import com.bri.config.SampleDbBuilder;
import com.bri.dao.*;
import com.bri.etl.DataExporter;
import com.bri.etl.GdeltImporter;
import com.bri.etl.ImportResult;
import com.bri.model.Country;
import com.bri.model.Event;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 快速烟雾测试 — 验证数据管理模块所有核心功能可运行。
 *
 * <p>运行方式：</p>
 * <pre>{@code
 * mvn compile exec:java -Dexec.mainClass="com.bri.SmokeTest"
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 */
public class SmokeTest {

    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "sample_bri.db";

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  数据管理模块 — 快速烟雾测试              ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // ===== 步骤1: 建库 =====
        Path dbFile = Path.of(dbPath);
        if (!Files.exists(dbFile)) {
            System.out.println("[1/5] 生成样例数据库...");
            SampleDbBuilder.build(dbFile);
        } else {
            System.out.println("[1/5] 数据库已存在: " + dbFile.toAbsolutePath());
        }

        // ===== 步骤2: DAO 查询 =====
        System.out.println("[2/5] 打开数据库...");
        DaoFactory f = DaoFactory.getInstance(dbPath);
        EventDao eDao = f.eventDao();
        CountryDao cDao = f.countryDao();

        long coop = eDao.countEvents(true);
        long total = eDao.countEvents(false);
        int countries = cDao.listBriCountries().size();
        System.out.printf("  沿线国家: %d | 合作事件: %d | 全部事件: %d\n\n",
                countries, coop, total);

        // ===== 步骤2.5: 批量导入演示 =====
        System.out.println("[2.5/5] 批量目录导入演示...");
        Path batchDir = Path.of("target/batch_demo");
        Files.createDirectories(batchDir);

        // 生成 2 个模拟 GDELT 日文件
        String tsvLine = buildTsvLine("99999001", "20250601", "CHN", "KAZ",
                "0421", "04", "8.0", "120", "2.5", "39.9", "116.4");
        String tsvLine2 = buildTsvLine("99999002", "20250602", "RUS", "UKR",
                "1011", "10", "-7.0", "300", "-3.0", "50.4", "30.5");
        Files.writeString(batchDir.resolve("20250601.export.CSV"), tsvLine + "\n" + tsvLine2);
        Files.writeString(batchDir.resolve("20250602.export.CSV"), tsvLine + "\n");

        GdeltImporter importer = new GdeltImporter();
        f.getDataSource().initIfAbsent();  // 确保 schema 就绪
        ImportResult batchResult = importer.importDirectory(batchDir,
                f.getDataSource(),
                pct -> {});  // 静默进度

        System.out.printf("  目录文件: 2 个 | 总计入库: %d 条 | 总耗时: %dms\n\n",
                batchResult.getImportedRows(), batchResult.getElapsedMs());

        // ===== 步骤3: 组合查询 =====
        System.out.println("[3/5] 组合查询（中国 2025 合作事件）...");
        EventQuery q = new EventQuery.Builder()
                .dateFrom("2025-01-01").dateTo("2025-06-30")
                .actor1("CHN").isCoop(1).limit(5)
                .build();
        List<Event> events = eDao.query(q);
        for (Event e : events) {
            Country c2 = cDao.getByCode(e.getActor2Code());
            System.out.printf("  %s | CHN → %s(%s) | Goldstein=%.1f\n",
                    e.getEventDate(),
                    c2 != null ? c2.getNameCn() : e.getActor2Code(),
                    e.getActor2Code(),
                    e.getGoldstein());
        }
        System.out.println();

        // ===== 步骤4: 模糊搜索 =====
        System.out.println("[4/5] 模糊搜索 '伊' ...");
        cDao.fuzzySearch("伊").forEach(c ->
                System.out.printf("  %-8s %-12s [%s]  %s\n",
                        c.getNameCn(), c.getNameEn(),
                        c.getCameoCode(),
                        cDao.subregionNames().get(c.getSubregion())));
        System.out.println();

        // ===== 步骤5: 导出 =====
        System.out.println("[5/5] 导出 CSV + XLSX ...");
        DataExporter exporter = new DataExporter();
        Path csv = Path.of("target/smoke_demo.csv");
        Path xlsx = Path.of("target/smoke_demo.xlsx");
        exporter.exportCsv(events, csv);
        exporter.exportXlsx(events, xlsx);
        System.out.printf("  CSV:  %s (%d bytes)\n", csv, Files.size(csv));
        System.out.printf("  XLSX: %s (%d bytes)\n", xlsx, Files.size(xlsx));

        f.close();
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  ✅ 全部烟雾测试通过                     ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }

    /** 构造一条 55 列的 TSV 行（用于演示批量导入）。 */
    private static String buildTsvLine(String eventId, String sqlDate,
                                       String actor1, String actor2,
                                       String eventCode, String eventRoot,
                                       String goldstein, String numMentions,
                                       String avgTone, String lat, String lon) {
        // GDELT 2.0 标准 61 列布局
        String[] fields = new String[61];
        for (int i = 0; i < 61; i++) fields[i] = "";
        fields[0]  = eventId;
        fields[1]  = sqlDate;
        fields[7]  = actor1;
        fields[17] = actor2;
        fields[26] = eventCode;
        fields[28] = eventRoot;
        fields[30] = goldstein;
        fields[31] = numMentions;
        fields[34] = avgTone;
        fields[56] = lat;
        fields[57] = lon;
        return String.join("\t", fields);
    }
}
