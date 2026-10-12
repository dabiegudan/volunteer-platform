package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.WeekConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WeekConfigMapper extends BaseMapper<WeekConfig> {

    @Select("SELECT * FROM week_config WHERE semester_id = #{semesterId} LIMIT 1")
    WeekConfig findBySemesterId(@Param("semesterId") String semesterId);

    @Select("SELECT * FROM week_config WHERE status = 1 ORDER BY semester_start_date DESC LIMIT 1")
    WeekConfig findActiveSemester();

    @Select("SELECT * FROM week_config ORDER BY semester_start_date DESC")
    List<WeekConfig> findAllSemesters();
}
