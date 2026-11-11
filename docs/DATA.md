# 数据说明

## 数据来源：GDELT 2.0

[GDELT](https://www.gdeltproject.org/)（Global Database of Events, Language, and Tone）
是一个持续采集全球新闻事件并编码为结构化数据的开放项目。

- 事件数据地址：<http://data.gdeltproject.org/gdeltv2/>
- 文件命名：`YYYYMMDDHHMMSS.export.CSV.zip`（每 15 分钟一个文件）
- 解压后为 **TSV，61 列，无表头**，字段含义见
  [GDELT Event Codebook](http://data.gdeltproject.org/documentation/GDELT-Event_Codebook-V2.0.pdf)

本项目用到的关键列（0-based）：

| 列 | 含义 |
|----|------|
| 1 | Day（事件日期 `YYYYMMDD`） |
| 7 / 16 | Actor1 / Actor2 国家代码 |
| 26–28 | EventCode / EventBaseCode / EventRootCode（CAMEO 编码） |
| 30 | GoldsteinScale（-10 ~ +10，正=合作，负=冲突） |
| 34 | AvgTone（-100 ~ +100，媒体情感） |
| 60 | DATEADDED |

## 仓库内的示例数据

`data/sample/` 下有 3 个文件：

```
data/sample/bri-events-01.export.CSV   # 1995 行
data/sample/bri-events-02.export.CSV   # 1995 行
data/sample/bri-events-03.export.CSV   # 1995 行
```

- 取自 **16 个真实的 GDELT 2.0 `export.CSV` 时间片**（2026-11 的 15 分钟粒度文件），
  只保留 Actor1 / Actor2 至少一方为一带一路沿线国家的事件，去重后共 **5 985 条**、约 2.4 MB。
- 事件日期范围：**2025-11-05 ~ 2026-11-10**（GDELT 会把历史事件回填到当前时间片）。
- 字段为完整的 **61 列** GDELT 2.0 格式，未做任何裁剪或改写。
- 仓库不包含完整数据集（单日数据即可达数十 MB，会超过 GitHub 单文件 100 MB 限制）。

> 解析器同时兼容 61 列标准格式与 58 列旧版格式，会自动识别列布局
> （旧版无 ADM2 列，地理与 URL 列位置不同）。

## 导入数据

### 方式一：命令行（推荐，首次运行）

```bash
# 在仓库根目录执行，会生成 bri.db
make demo-db

# 或等价的手动命令
mvn -q -DskipTests compile
mvn -q exec:java -Dexec.mainClass=com.bri.BulkImport -Dexec.args="data/sample bri.db"
```

### 方式二：界面导入

启动程序后进入「📂 数据管理」面板：

- **导入文件**：选择一个或多个 `*.export.CSV` 文件；
- **在线下载**：选择日期范围 → 扫描 → 下载 → 自动解压并导入（需要联网）；
- 导入完成后所有面板会自动刷新。

### 方式三：自行准备数据目录

```bash
# 把解压后的 export.CSV 放到任意目录，然后整目录导入
java -cp target/bri-analysis-1.0.0.jar com.bri.BulkImport /path/to/gdelt_data bri.db
```

> 导入时会自动**过滤非一带一路沿线国家**的事件，控制台会打印过滤数量。

## 数据库

- 文件：仓库根目录 `bri.db`（SQLite，自动建表）
- 表：`events`、`countries`、`subregions`、`downloaded_files`
- 已加入 `.gitignore`，不会进版本库
- 想重新开始：删除 `bri.db`、`bri.db-shm`、`bri.db-wal` 后重新导入

## 许可证提示

GDELT 数据的使用须遵守其官方条款；本项目仅对「数据 → 指标 → 可视化」的处理流程做教学演示，
不对数据本身的准确性负责。
