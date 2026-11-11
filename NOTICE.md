# 第三方组件与素材说明

本项目在构建与运行时会使用以下第三方库。**它们各自的许可证独立于本仓库的 LICENSE**，
本文件仅作汇总说明，最终以各项目官方声明为准。

## 运行时依赖

| 组件 | 版本 | 许可证 |
|------|------|--------|
| OpenJFX（JavaFX Controls / FXML / Graphics） | 23.0.1 | GPL-2.0 with Classpath Exception |
| Xerial SQLite JDBC | 3.45.3.0 | Apache-2.0 |
| HikariCP | 5.1.0 | Apache-2.0 |
| UCanAccess | 5.0.0 | Apache-2.0 |
| OpenCSV | 5.9 | Apache-2.0 |
| Jackson Databind | 2.17.2 | Apache-2.0 |
| Weka (weka-stable) | 3.8.6 | **GPL-3.0** |
| Apache POI (poi-ooxml) | 5.2.5 | Apache-2.0 |
| SLF4J Simple | 2.0.9 | MIT |

## 仅测试依赖

| 组件 | 版本 | 许可证 |
|------|------|--------|
| JUnit 4 | 4.13.2 | EPL-1.0 |

## 关于本项目许可证

由于运行依赖中包含 **Weka（GPL-3.0）**，本项目整体以 **GPL-3.0** 发布（见 LICENSE）。
如果你希望改用 MIT / Apache-2.0 等宽松许可证，需要先移除对 Weka 的依赖
（`pom.xml` 与 `com.bri.engine.WekaClusteringStrategy`），并用自实现的聚类算法替代。

## 数据与素材

- **GDELT 2.0 数据**：由 [The GDELT Project](https://www.gdeltproject.org/) 公开发布，
  使用须遵守其[使用条款](https://www.gdeltproject.org/about.html)。`data/sample/` 下的文件
  为 GDELT 原始数据的截取样本（每个文件前 2500 行），仅用于演示。
- **`src/main/resources/images/world_map.png`**：世界地图底图。
  ⚠️ 发布到公开仓库前请确认其版权与授权来源；若来源不明，建议替换为
  [Natural Earth](https://www.naturalearthdata.com/)（公有领域）等可自由使用的底图。
