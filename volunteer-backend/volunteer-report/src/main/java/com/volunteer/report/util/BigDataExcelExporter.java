package com.volunteer.report.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * 大数据量Excel导出工具
 * 使用SXSSFWorkbook流式写入，避免内存溢出
 */
@Slf4j
public class BigDataExcelExporter implements AutoCloseable {

    private final SXSSFWorkbook workbook;

    public BigDataExcelExporter(int rowAccessWindowSize) {
        this.workbook = new SXSSFWorkbook(rowAccessWindowSize);
        this.workbook.setCompressTempFiles(true);
    }

    public BigDataExcelExporter() {
        this(100);
    }

    public SXSSFWorkbook getWorkbook() {
        return workbook;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void close() {
        if (workbook != null) {
            workbook.dispose();
            log.info("SXSSFWorkbook disposed successfully");
        }
    }
}
