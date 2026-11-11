package com.bri.config;

import com.bri.model.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 样例数据库构建器 — 生成 {@code sample_bri.db} 供团队联调使用。
 *
 * <p>构建 db 包含完整 schema、82 个沿线国家、约 2000 条模拟事件。
 * 覆盖五大子区域、合作/冲突/中性三种事件类型，可立即用于查询、
 * 分析和可视化模块的独立开发与联调。</p>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * // 方式一：直接运行 main 方法
 * mvn compile exec:java -Dexec.mainClass="com.bri.config.SampleDbBuilder"
 *
 * // 方式二：写在代码中
 * SampleDbBuilder.build(Path.of("sample_bri.db"));
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 */
public final class SampleDbBuilder {

    private SampleDbBuilder() {}

    /** 生成的模拟事件条数 */
    private static final int SAMPLE_EVENT_COUNT = 2000;

    /** 模拟日期起点 */
    private static final String[] DATES = {
            "2025-01-05","2025-01-15","2025-02-03","2025-02-18",
            "2025-03-07","2025-03-22","2025-04-10","2025-04-25",
            "2025-05-02","2025-05-19","2025-06-01","2025-06-15"
    };

    /**
     * 构建 sample_bri.db 并返回输出路径。
     *
     * @param dest 目标文件路径，如 Path.of("sample_bri.db")
     * @return 生成的 db 文件路径
     * @throws IOException 写入失败
     */
    public static Path build(Path dest) throws IOException {
        // 删除旧文件
        Files.deleteIfExists(dest);

        DataSource ds = DataSource.getInstance(dest.toString());
        ds.initIfAbsent();

        // 1. 加载沿线国家
        CountryConfigLoader loader = new CountryConfigLoader();
        int countryCount = loader.loadToDatabase(ds);

        // 2. 补充中国
        loader.writeToDatabase(ds, List.of(
                new com.bri.model.Country("CHN", "中国", "China", "EE", 0)
        ));

        // 3. 生成模拟事件
        int eventCount = generateSampleEvents(ds);

        ds.close();
        System.out.printf("✅ sample_bri.db 已生成: %s (%.1f KB)\n",
                dest.toAbsolutePath(),
                Files.size(dest) / 1024.0);
        System.out.printf("   国家: %d | 事件: %d\n", countryCount, eventCount);

        return dest;
    }

    /**
     * 生成约 2000 条模拟 GDELT 事件。
     */
    private static int generateSampleEvents(DataSource ds) {
        Random rnd = new Random(42); // 固定种子，可复现
        String[] briCodes = {
                "CHN","KAZ","KGZ","UZB","RUS","UKR","IRN","IRQ",
                "SAU","TUR","EGY","ZAF","ETH","KEN","VNM","THA",
                "IDN","MYS","POL","HUN","SRB","DZA","NGA","TZA"
        };
        String[][] coopPairs = {
                {"CHN","KAZ"},{"CHN","RUS"},{"CHN","EGY"},{"CHN","VNM"},
                {"CHN","THA"},{"CHN","IDN"},{"CHN","ZAF"},{"CHN","ETH"},
                {"RUS","KAZ"},{"RUS","SRB"},{"CHN","HUN"},{"CHN","POL"},
                {"TUR","IRN"},{"SAU","EGY"},{"CHN","TZA"},{"CHN","NGA"}
        };
        String[][] conflictPairs = {
                {"RUS","UKR"},{"IRN","IRQ"},{"IRN","SAU"},
                {"TUR","IRQ"},{"ETH","KEN"},{"CHN","VNM"},
                {"RUS","POL"},{"DZA","EGY"},{"NGA","TZA"}
        };
        String[][] rootCoop = {{"04","0421"},{"04","0401"},{"05","0511"},{"06","0611"}};
        String[][] rootConflict = {{"10","1011"},{"12","1211"},{"14","1414"}};

        int eventId = 10000000;
        List<Event> batch = new ArrayList<>(1000);

        for (int i = 0; i < SAMPLE_EVENT_COUNT; i++) {
            boolean isCoop = rnd.nextDouble() < 0.7; // 70% 合作
            String date = DATES[rnd.nextInt(DATES.length)];

            String a1, a2, root, code;
            double goldstein;
            if (isCoop) {
                String[] pair = coopPairs[rnd.nextInt(coopPairs.length)];
                a1 = pair[0]; a2 = pair[1];
                String[] rc = rootCoop[rnd.nextInt(rootCoop.length)];
                root = rc[0]; code = rc[1];
                goldstein = 2.0 + rnd.nextDouble() * 8.0;
            } else {
                String[] pair = conflictPairs[rnd.nextInt(conflictPairs.length)];
                a1 = pair[0]; a2 = pair[1];
                String[] rc = rootConflict[rnd.nextInt(rootConflict.length)];
                root = rc[0]; code = rc[1];
                goldstein = -10.0 + rnd.nextDouble() * 5.0;
            }

            Event e = new Event(eventId++, date, a1, a2, root, code,
                    isCoop ? 1 : 0, goldstein,
                    -5.0 + rnd.nextDouble() * 10.0,
                    10 + rnd.nextInt(500), null);
            // 随机坐标
            double lat = -30 + rnd.nextDouble() * 90;
            double lon = -10 + rnd.nextDouble() * 130;
            if (rnd.nextDouble() < 0.85) { // 85% 有坐标
                e.setLat(lat);
                e.setLon(lon);
            }
            batch.add(e);

            if (batch.size() >= 1000) {
                flushBatch(ds, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(ds, batch);
        }
        return SAMPLE_EVENT_COUNT;
    }

    private static void flushBatch(DataSource ds, List<Event> batch) {
        String sql = "INSERT INTO events(event_id, event_date, actor1_code, actor2_code,"
                + " event_root, event_code, is_coop, goldstein, avg_tone, num_mentions, lat, lon)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
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
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("模拟事件写入失败", e);
        }
    }

    /**
     * 命令行入口。
     */
    public static void main(String[] args) throws IOException {
        Path dest = args.length > 0 ? Path.of(args[0])
                : Path.of("sample_bri.db");
        build(dest);
    }
}
