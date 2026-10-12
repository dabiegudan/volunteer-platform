package com.volunteer.report.dto;

import java.util.List;

/**
 * 导出请求DTO
 */
public class ExportRequestDTO {

    /**
     * 导出类型
     */
    private String exportType;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 选择的列
     */
    private List<ExportColumnDTO> columns;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 是否包含汇总
     */
    private Boolean includeSummary;

    /**
     * 导出格式：EXCEL, CSV
     */
    private String format;

    /**
     * 其他筛选条件
     */
    private String filters;

    public String getExportType() {
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public List<ExportColumnDTO> getColumns() {
        return columns;
    }

    public void setColumns(List<ExportColumnDTO> columns) {
        this.columns = columns;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Boolean getIncludeSummary() {
        return includeSummary;
    }

    public void setIncludeSummary(Boolean includeSummary) {
        this.includeSummary = includeSummary;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }
}
