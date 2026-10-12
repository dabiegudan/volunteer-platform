package com.volunteer.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.volunteer.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * #7 GIS志愿地图与15分钟服务圈 - 志愿服务站实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_station")
public class ServiceStation extends BaseEntity {

    private String stationName;

    private String address;

    private BigDecimal gpsLat;

    private BigDecimal gpsLng;

    private String serviceHours;

    private String contactPhone;

    private String services;

    private Integer status;
}