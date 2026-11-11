package com.bri;

/**
 * 一带一路沿线国家合作态势分析系统（BRI Analyzer）备用入口类。
 *
 * <p>本类委托 {@code com.yourteam.bri.MainApp} 启动 JavaFX 应用主窗口。
 * 为兼容旧调用方式保留，新代码请直接使用 {@code com.yourteam.bri.MainApp}。</p>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * mvn javafx:run
 * }</pre>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.yourteam.bri.MainApp
 */
public class Main {

    /**
     * 应用入口。
     *
     * @param args 命令行参数（暂未使用）
     */
    public static void main(String[] args) {
        com.yourteam.bri.MainApp.main(args);
    }
}
