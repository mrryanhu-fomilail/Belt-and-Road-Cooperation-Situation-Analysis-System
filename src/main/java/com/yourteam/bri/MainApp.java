/**
 * 一带一路合作态势分析系统 - 程序入口
 * <p>
 * 本系统基于 GDELT（Global Database of Events, Language, and Tone）开放数据，
 * 使用 JavaFX 构建可视化分析平台，对一带一路沿线国家的合作态势进行多维度分析。
 * </p>
 *
 * <h2>功能模块</h2>
 * <ul>
 *   <li>仪表盘总览（DashboardPanel）：关键指标概览 + 合作热点/风险预警 TOP5</li>
 *   <li>世界地图可视化（MapPanel）：热力图展示合作指数 + 合作路线 + 交互式国家气泡</li>
 *   <li>事件查询检索（QueryPanel）：多条件组合查询 GDELT 事件 + 结果饼图</li>
 *   <li>新闻动态列表（NewsPanel）：情感分类展示 + 筛选分页 + 详情弹窗</li>
 *   <li>合作指数排行榜（RankingPanel）：柱状图排名 + 区域筛选</li>
 *   <li>趋势分析（TrendChartPanel）：多系列折线图 + 统计摘要</li>
 *   <li>区域对比雷达图（RadarPanel）：五大区域六维指标蜘蛛网图</li>
 *   <li>聚类分析与风险评估（ClusteringPanel）：K-Means 分类卡片 + 风险表格</li>
 *   <li>时间轴控制（TimelinePanel）：双滑块日期选择 + 自动播放 + 关键事件标记</li>
 * </ul>
 *
 * <h2>数据来源</h2>
 * <p>
 * 所有数据由 DataService 提供模拟数据，使用固定种子（Random(42)）保证可重复性。
 * 实际运行时需替换为 GDELT 真实数据源或数据库。
 * </p>
 *
 * @see com.yourteam.bri.gui.MainWindow JavaFX 主窗口（界面启动入口）
 * @see com.yourteam.bri.service.DataService 模拟数据服务层
 */
package com.yourteam.bri;

import com.yourteam.bri.gui.MainWindow;

/**
 * 系统启动类 - 显示控制台欢迎信息后启动 JavaFX 图形界面
 */
public class MainApp {
    /**
     * 程序入口方法
     * <ol>
     *   <li>打印系统标题和控制台提示</li>
     *   <li>调用 MainWindow.main() 启动 JavaFX 图形用户界面</li>
     * </ol>
     *
     * @param args 命令行参数（当前未使用）
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  一带一路合作态势分析系统");
        System.out.println("  基于 GDELT 数据分析");
        System.out.println("========================================");
        
        // 启动 JavaFX GUI 主窗口
        MainWindow.main(args);
    }
}