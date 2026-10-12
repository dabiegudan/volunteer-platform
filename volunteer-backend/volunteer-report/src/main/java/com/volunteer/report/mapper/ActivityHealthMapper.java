package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.ActivityHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ActivityHealthMapper extends BaseMapper<ActivityHealth> {

    @Select("SELECT * FROM activity_health " +
            "WHERE activity_id = #{activityId} AND diagnosis_date = #{date} LIMIT 1")
    ActivityHealth findByActivityIdAndDate(@Param("activityId") Long activityId, @Param("date") LocalDate date);

    @Select("SELECT * FROM activity_health WHERE activity_id = #{activityId} ORDER BY diagnosis_date DESC")
    List<ActivityHealth> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM activity_health WHERE health_status = #{status} ORDER BY diagnosis_date DESC")
    List<ActivityHealth> findByHealthStatus(@Param("status") String status);

    @Select("SELECT * FROM activity_health WHERE diagnosis_date = #{date} ORDER BY overall_score ASC")
    List<ActivityHealth> findByDate(@Param("date") LocalDate date);
}
