# 架构说明

本项目由两套包结构组成，核心思路是**后端数据/分析能力**与**JavaFX 界面**分离：

| 包 | 角色 | 说明 |
|----|------|------|
| `com.bri.*` | 后端 | 数据库、ETL、分析引擎、报表导出、命令行工具，不依赖 JavaFX 界面代码 |
| `com.yourteam.bri.*` | 前端 | JavaFX 界面（10 个功能面板）、服务层、国家基础数据 |
| `com.bri.engine.BriAnalysisEngine` | 粘合层 | 被 `BriEngineHolder` 单例持有，为界面提供聚合指标 |

## 数据流

```
GDELT 2.0 export.CSV（TSV，61 列，无表头）
        │
        │  GdeltImporter / BulkImport（CLI）/ DataImportPanel（GUI）
        ▼
   SQLite 数据库  bri.db（WAL 模式）
   ├── events            事件表
   ├── countries         沿线国家表
   ├── subregions        子区域表
   └── downloaded_files  下载记录表
        │
        │  DaoFactory（单例）→ EventDao / CountryDao
        ▼
   DataService（带内存缓存 + 全局时间范围过滤）
        │
        ▼
   JavaFX 面板（仪表盘 / 地图 / 趋势 / 排名 / 雷达 / 聚类 / 报告 …）
```

`BriAnalysisEngine` 负责预计算：合作指数、月度统计、聚类、风险排序、关联规则，
界面通过 `BriEngineHolder` 取用，避免每个面板重复全表扫描。

## 关键类

| 类 | 职责 |
|----|------|
| `com.bri.config.DataSource` | HikariCP + SQLite 连接池单例，`initIfAbsent()` 幂等建表 |
| `com.bri.config.SQLiteSchema` | 建表 DDL |
| `com.bri.etl.GdeltImporter` | 解析 GDELT TSV、过滤非沿线国家、批量入库 |
| `com.bri.download.GdeltDownloadService` | 按日期范围扫描/下载 GDELT 压缩包，带本地缓存 |
| `com.bri.engine.BriAnalysisEngine` | 指数计算、聚合、导出 JSON |
| `com.bri.engine.WekaClusteringStrategy` | 基于 Weka 的 K-Means 国家聚类 |
| `com.yourteam.bri.gui.MainWindow` | 主窗口：侧边导航、内容区、时间轴、状态栏 |
| `com.yourteam.bri.service.BriEngineHolder` | 引擎单例 + 数据变更通知 |
| `com.yourteam.bri.util.BRICountries` | 沿线国家代码/中文名/区域映射 |

## 界面模块

侧边栏共 10 个模块：总览仪表盘、数据管理、事件查询、趋势分析、指数排名、
专题地图、区域雷达图、聚类与风险、关联规则、年度报告。

所有面板在数据导入完成后通过 `DataChangeNotifier` 收到通知并自动刷新。

## 运行期约定

- 数据库路径固定为**当前工作目录**下的 `bri.db`（`DataSource.getInstance("bri.db")`），
  因此请始终在仓库根目录执行 `mvn javafx:run`。
- 数据库文件、下载缓存、导出产物都已加入 `.gitignore`，不会被误提交。
