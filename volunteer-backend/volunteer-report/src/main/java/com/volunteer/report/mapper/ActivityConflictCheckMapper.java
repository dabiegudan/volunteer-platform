package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.ActivityConflictCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActivityConflictCheckMapper extends BaseMapper<ActivityConflictCheck> {

    @Select("SELECT * FROM activity_conflict_check WHERE user_id = #{userId} ORDER BY check_time DESC")
    List<ActivityConflictCheck> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM activity_conflict_check WHERE activity_id = #{activityId} ORDER BY check_time DESC")
    List<ActivityConflictCheck> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM activity_conflict_check " +
            "WHERE user_id = #{userId} AND activity_id = #{activityId} " +
            "ORDER BY check_time DESC LIMIT 1")
    ActivityConflictCheck findByUserIdAndActivityId(@Param("userId") Long userId, @Param("activityId") Long activityId);

    @Select("SELECT * FROM activity_conflict_check " +
            "WHERE user_id = #{userId} AND has_conflict = 1 " +
            "ORDER BY check_time DESC")
    List<ActivityConflictCheck> findConflictsByUserId(@Param("userId") Long userId);
}
