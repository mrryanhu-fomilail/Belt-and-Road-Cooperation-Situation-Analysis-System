# 贡献指南

## 环境

- JDK 23+、Maven 3.8+
- 推荐 IntelliJ IDEA（打开仓库根目录，等待 Maven 自动导入）

## 本地开发流程

```bash
git checkout -b feat/你的功能名
make demo-db        # 首次：生成示例数据库
mvn javafx:run      # 启动界面
```

## 提交前检查

1. **能编译**：`mvn -B -DskipTests package` 通过（CI 会在 macOS / Windows / Linux 上各跑一次）。
2. **不提交本地文件**：`target/`、`bri.db*`、`.gdelt_master_cache.txt`、`output/` 都已在 `.gitignore` 中，
   请不要用 `git add -f` 强行提交。
3. **不提交真实数据**：GDELT 完整数据集体积很大，请放在本地或 Release 附件里。
4. **新增依赖**：同步更新 `NOTICE.md` 的许可证清单。

## 代码约定

- 包结构：后端逻辑放 `com.bri.*`，JavaFX 界面放 `com.yourteam.bri.gui.*`。
- 界面不要直接写 SQL，统一通过 `DaoFactory` / `EventDao` / `CountryDao` 访问数据库。
- 中文注释、UTF-8 编码。
- 新增面板需在 `MainWindow` 注册导航项，并在数据变更时通过 `DataChangeNotifier` 刷新。

## Commit message 建议

```
<type>(<scope>): <简短说明>

type: feat | fix | docs | refactor | test | chore
scope: engine | gui | etl | dao | docs ...
```

示例：`feat(gui): 地图支持按区域筛选气泡`
