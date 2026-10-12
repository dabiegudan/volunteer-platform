package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.DiagnosisReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DiagnosisReportMapper extends BaseMapper<DiagnosisReport> {

    @Select("SELECT * FROM diagnosis_report WHERE activity_id = #{activityId} ORDER BY generated_at DESC")
    List<DiagnosisReport> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM diagnosis_report WHERE report_type = #{type} ORDER BY generated_at DESC")
    List<DiagnosisReport> findByReportType(@Param("type") String type);

    @Select("SELECT * FROM diagnosis_report " +
            "WHERE activity_id = #{activityId} " +
            "ORDER BY generated_at DESC LIMIT 1")
    DiagnosisReport findLatestByActivityId(@Param("activityId") Long activityId);
}
