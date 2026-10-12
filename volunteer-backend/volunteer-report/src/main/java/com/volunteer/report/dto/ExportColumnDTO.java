package com.volunteer.report.dto;

import lombok.Data;

/**
 * 导出列配置DTO
 */
@Data
public class ExportColumnDTO {

    /**
     * 列键名
     */
    private String key;

    /**
     * 列显示名称
     */
    private String label;

    /**
     * 列宽度（字符数）
     */
    private Integer width;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序
     */
    private Integer order;
}
