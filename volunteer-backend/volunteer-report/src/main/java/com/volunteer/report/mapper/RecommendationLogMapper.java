package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.RecommendationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecommendationLogMapper extends BaseMapper<RecommendationLog> {

    @Select("SELECT * FROM recommendation_log WHERE user_id = #{userId} ORDER BY action_time DESC")
    List<RecommendationLog> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM recommendation_log WHERE activity_id = #{activityId} ORDER BY action_time DESC")
    List<RecommendationLog> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT COUNT(*) FROM recommendation_log " +
            "WHERE user_id = #{userId} AND action = #{action}")
    int countByUserIdAndAction(@Param("userId") Long userId, @Param("action") String action);

    @Select("SELECT COUNT(*) FROM recommendation_log " +
            "WHERE activity_id = #{activityId} AND action = #{action}")
    int countByActivityIdAndAction(@Param("activityId") Long activityId, @Param("action") String action);
}
