package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.ActivityMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ActivityMetricMapper extends BaseMapper<ActivityMetric> {

    @Select("SELECT * FROM activity_metric WHERE activity_id = #{activityId} ORDER BY metric_date DESC")
    List<ActivityMetric> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM activity_metric " +
            "WHERE activity_id = #{activityId} " +
            "  AND metric_date BETWEEN #{startDate} AND #{endDate} " +
            "ORDER BY metric_date DESC")
    List<ActivityMetric> findByActivityIdAndDateRange(@Param("activityId") Long activityId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    @Select("SELECT * FROM activity_metric " +
            "WHERE activity_id = #{activityId} AND metric_type = #{type} AND metric_date = #{date} LIMIT 1")
    ActivityMetric findByActivityIdAndTypeAndDate(@Param("activityId") Long activityId,
                                                  @Param("type") String type,
                                                  @Param("date") LocalDate date);

    @Select("SELECT * FROM activity_metric WHERE metric_type = #{type} ORDER BY metric_date DESC")
    List<ActivityMetric> findByType(@Param("type") String type);
}
