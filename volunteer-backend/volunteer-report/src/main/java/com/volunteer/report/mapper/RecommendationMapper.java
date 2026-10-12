package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.Recommendation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface RecommendationMapper extends BaseMapper<Recommendation> {

    @Select("SELECT * FROM recommendation WHERE user_id = #{userId} ORDER BY score DESC, create_time DESC")
    List<Recommendation> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM recommendation WHERE user_id = #{userId} ORDER BY score DESC")
    List<Recommendation> findByUserIdOrderByScore(@Param("userId") Long userId);

    @Select("SELECT * FROM recommendation " +
            "WHERE user_id = #{userId} AND activity_id = #{activityId} LIMIT 1")
    Recommendation findByUserIdAndActivityId(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Delete("DELETE FROM recommendation WHERE expires_at IS NOT NULL AND expires_at < #{now}")
    int deleteExpired(@Param("now") java.time.LocalDateTime now);
}
