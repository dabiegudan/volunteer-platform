package com.volunteer.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * #15 操作审计日志 - 系统操作日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {

    private Long operatorId;

    private String operatorName;

    private String opType;

    private String opModule;

    private String opContent;

    private LocalDateTime opTime;

    private String ipAddress;

    private String result;

    private String errorMsg;
}