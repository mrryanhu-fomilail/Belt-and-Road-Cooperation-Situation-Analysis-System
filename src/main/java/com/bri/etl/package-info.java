/**
 * 数据导入与清洗包 — 负责 GDELT 2.0 TSV 数据的解析、验证、清洗与入库。
 *
 * <p>核心类（后续迭代实现）：</p>
 * <ul>
 *   <li>{@code TsvImporter} — TSV 文件流式解析，进度回调</li>
 *   <li>{@code DataCleaner} — 空值处理、异常经纬度过滤、is_coop 标记</li>
 *   <li>{@code BriFilter} — 一带一路沿线国家过滤器</li>
 *   <li>{@code DbWriter} — 批量写入 SQLite，事务管理</li>
 * </ul>
 *
 * <p>本包为数据管理模块内部实现，其他模块不应直接依赖。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 */
package com.bri.etl;
