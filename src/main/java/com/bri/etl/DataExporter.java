package com.bri.etl;

import com.bri.model.Event;
import com.bri.model.EventCategory;
import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 数据导出器 — 支持将事件列表导出为 CSV 或 Excel (.xlsx) 格式（F-04）。
 *
 * <h2>导出格式</h2>
 * <ul>
 *   <li><b>CSV</b> — UTF-8 编码，带 BOM 头（{@code ﻿}），Excel 直接打开中文不乱码</li>
 *   <li><b>XLSX</b> — Apache POI 生成，含自动列宽、数字格式化、中文表头</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * DataExporter exporter = new DataExporter();
 *
 * // CSV 导出
 * exporter.exportCsv(events, Path.of("results.csv"), percent -> {
 *     Platform.runLater(() -> progressBar.setProgress(percent));
 * });
 *
 * // XLSX 导出
 * exporter.exportXlsx(events, Path.of("results.xlsx"));
 * }</pre>
 *
 * <h2>线程安全</h2>
 * <p>无共享可变状态。每次调用创建独立的 Writer/Workbook，可在多线程中并发导出。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see Event
 * @see ProgressListener
 */
public class DataExporter {

    // ==================== 中文表头 ====================

    /** CSV/XLSX 共享的列标题 */
    static final String[] HEADERS = {
            "事件ID", "日期", "施动国", "受动国",
            "事件大类", "事件码", "合作/冲突",
            "Goldstein分值", "情感语调", "报道量",
            "纬度", "经度"
    };

    /** CSV 进度通知间隔行数 */
    private static final int PROGRESS_INTERVAL = 500;

    /** BOM 字节（UTF-8） */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // ==================== CSV 导出 ====================

    /**
     * 导出事件列表为 CSV 文件（含进度回调）。
     *
     * <p>输出 UTF-8 BOM + CSV，Excel 直接双击打开中文不乱码。</p>
     *
     * @param events   事件列表（空列表导出仅表头）
     * @param dest     目标文件路径（父目录不存在时自动创建）
     * @param listener 进度回调，null 表示不需要
     * @throws IOException 写入失败时抛出
     */
    public void exportCsv(List<Event> events, Path dest,
                          ProgressListener listener) throws IOException {
        Objects.requireNonNull(events, "事件列表不能为 null");
        Objects.requireNonNull(dest, "目标路径不能为 null");

        ensureParentDir(dest);
        int total = events.size();
        fireProgress(listener, 0.0);

        try (var fos = new FileOutputStream(dest.toFile());
             var bos = new BufferedOutputStream(fos);
             var osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {

            // 写入 BOM
            bos.write(BOM);

            CSVWriter writer = new CSVWriter(osw);

            // 写表头
            writer.writeNext(HEADERS, false);

            // 写数据行
            int written = 0;
            for (Event e : events) {
                writer.writeNext(toRow(e), false);
                written++;
                if (written % PROGRESS_INTERVAL == 0) {
                    fireProgress(listener, (double) written / total);
                }
            }
            writer.flush();
        }
        fireProgress(listener, 1.0);
    }

    /**
     * 导出事件列表为 CSV 文件（无进度回调的便捷重载）。
     *
     * @param events 事件列表
     * @param dest   目标文件路径
     * @throws IOException 写入失败时抛出
     */
    public void exportCsv(List<Event> events, Path dest) throws IOException {
        exportCsv(events, dest, null);
    }

    /**
     * 导出事件列表为 CSV，写入 OutputStream（供 Web/Servlet 场景使用）。
     *
     * <p>调用方负责关闭流。BOM 已写入。</p>
     *
     * @param events 事件列表
     * @param out    输出流
     * @throws IOException 写入失败时抛出
     */
    public void exportCsv(List<Event> events, OutputStream out) throws IOException {
        Objects.requireNonNull(events, "事件列表不能为 null");
        Objects.requireNonNull(out, "输出流不能为 null");

        out.write(BOM);
        var osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        CSVWriter writer = new CSVWriter(osw);
        writer.writeNext(HEADERS, false);
        for (Event e : events) {
            writer.writeNext(toRow(e), false);
        }
        writer.flush();
    }

    // ==================== XLSX 导出 ====================

    /**
     * 导出事件列表为 Excel .xlsx 文件（含进度回调）。
     *
     * <p>自动设置列宽和数字格式。生成的 .xlsx 可用 Excel/WPS 打开。</p>
     *
     * @param events   事件列表
     * @param dest     目标文件路径（.xlsx）
     * @param listener 进度回调，null 表示不需要
     * @throws IOException 写入失败时抛出
     */
    public void exportXlsx(List<Event> events, Path dest,
                           ProgressListener listener) throws IOException {
        Objects.requireNonNull(events, "事件列表不能为 null");
        Objects.requireNonNull(dest, "目标路径不能为 null");

        ensureParentDir(dest);
        int total = events.size();
        fireProgress(listener, 0.0);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("事件数据");

            // 表头样式
            CellStyle headerStyle = createHeaderStyle(wb);

            // 写表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 数字格式
            CellStyle intStyle = wb.createCellStyle();
            intStyle.setDataFormat(
                    wb.getCreationHelper().createDataFormat().getFormat("0"));

            CellStyle doubleStyle = wb.createCellStyle();
            doubleStyle.setDataFormat(
                    wb.getCreationHelper().createDataFormat().getFormat("0.00"));

            // 写数据行
            int rowIdx = 1;
            for (Event e : events) {
                Row row = sheet.createRow(rowIdx);
                fillRow(row, e, intStyle, doubleStyle);
                rowIdx++;
                if (rowIdx % PROGRESS_INTERVAL == 0) {
                    fireProgress(listener, (double) rowIdx / total);
                }
            }

            // 自动列宽
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 冻结首行
            sheet.createFreezePane(0, 1);

            // 写入文件
            try (var fos = new FileOutputStream(dest.toFile());
                 var bos = new BufferedOutputStream(fos)) {
                wb.write(bos);
            }
        }
        fireProgress(listener, 1.0);
    }

    /**
     * 导出事件列表为 Excel .xlsx 文件（无进度回调的便捷重载）。
     *
     * @param events 事件列表
     * @param dest   目标文件路径
     * @throws IOException 写入失败时抛出
     */
    public void exportXlsx(List<Event> events, Path dest) throws IOException {
        exportXlsx(events, dest, null);
    }

    /**
     * 导出事件列表为 XLSX，写入 OutputStream。
     *
     * @param events 事件列表
     * @param out    输出流（调用方负责关闭）
     * @throws IOException 写入失败时抛出
     */
    public void exportXlsx(List<Event> events, OutputStream out) throws IOException {
        Objects.requireNonNull(events, "事件列表不能为 null");
        Objects.requireNonNull(out, "输出流不能为 null");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("事件数据");
            CellStyle headerStyle = createHeaderStyle(wb);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle intStyle = wb.createCellStyle();
            intStyle.setDataFormat(
                    wb.getCreationHelper().createDataFormat().getFormat("0"));

            CellStyle doubleStyle = wb.createCellStyle();
            doubleStyle.setDataFormat(
                    wb.getCreationHelper().createDataFormat().getFormat("0.00"));

            int rowIdx = 1;
            for (Event e : events) {
                Row row = sheet.createRow(rowIdx);
                fillRow(row, e, intStyle, doubleStyle);
                rowIdx++;
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 将 Event 转换为 CSV 字符串数组（一行）。
     */
    private String[] toRow(Event e) {
        return new String[]{
                String.valueOf(e.getEventId()),
                e.getEventDate(),
                nvl(e.getActor1Code()),
                nvl(e.getActor2Code()),
                nvl(e.getEventRoot()),
                nvl(e.getEventCode()),
                EventCategory.fromDbValue(e.getIsCoop()).getDescription(),
                String.valueOf(e.getGoldstein()),
                String.valueOf(e.getAvgTone()),
                String.valueOf(e.getNumMentions()),
                e.getLat() == 0.0 && e.getLon() == 0.0 ? "" : String.valueOf(e.getLat()),
                e.getLat() == 0.0 && e.getLon() == 0.0 ? "" : String.valueOf(e.getLon())
        };
    }

    /**
     * 填充 Excel 一行数据。
     */
    private void fillRow(Row row, Event e,
                         CellStyle intStyle, CellStyle doubleStyle) {
        int i = 0;
        createCell(row, i++).setCellValue(e.getEventId());

        createCell(row, i++).setCellValue(e.getEventDate());
        createCell(row, i++).setCellValue(nvl(e.getActor1Code()));
        createCell(row, i++).setCellValue(nvl(e.getActor2Code()));
        createCell(row, i++).setCellValue(nvl(e.getEventRoot()));
        createCell(row, i++).setCellValue(nvl(e.getEventCode()));
        createCell(row, i++).setCellValue(
                EventCategory.fromDbValue(e.getIsCoop()).getDescription());

        Cell goldstein = createCell(row, i++);
        goldstein.setCellValue(e.getGoldstein());
        goldstein.setCellStyle(doubleStyle);

        Cell avgTone = createCell(row, i++);
        avgTone.setCellValue(e.getAvgTone());
        avgTone.setCellStyle(doubleStyle);

        Cell mentions = createCell(row, i++);
        mentions.setCellValue(e.getNumMentions());
        mentions.setCellStyle(intStyle);

        if (!(e.getLat() == 0.0 && e.getLon() == 0.0)) {
            Cell lat = createCell(row, i);
            lat.setCellValue(e.getLat());
            lat.setCellStyle(doubleStyle);
        }
        i++;

        if (!(e.getLat() == 0.0 && e.getLon() == 0.0)) {
            Cell lon = createCell(row, i);
            lon.setCellValue(e.getLon());
            lon.setCellStyle(doubleStyle);
        }
    }

    /**
     * 创建 Excel 表头样式（粗体 + 浅灰背景 + 边框）。
     */
    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private Cell createCell(Row row, int col) {
        return row.createCell(col);
    }

    /** null → 空字符串 */
    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 确保父目录存在 */
    private void ensureParentDir(Path dest) throws IOException {
        Path parent = dest.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
    }

    /** 安全触发进度回调 */
    private void fireProgress(ProgressListener l, double pct) {
        if (l != null) {
            try { l.onProgress(pct); } catch (Exception ignored) {}
        }
    }
}
