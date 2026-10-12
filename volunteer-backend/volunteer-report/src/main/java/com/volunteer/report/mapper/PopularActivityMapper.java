package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.PopularActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PopularActivityMapper extends BaseMapper<PopularActivity> {

    @Select("SELECT * FROM popular_activity " +
            "WHERE activity_id = #{activityId} AND date = #{date} LIMIT 1")
    PopularActivity findByActivityIdAndDate(@Param("activityId") Long activityId, @Param("date") LocalDate date);

    @Select("SELECT * FROM popular_activity WHERE date = #{date} ORDER BY hot_score DESC, id ASC")
    List<PopularActivity> findByDateOrderByHotScore(@Param("date") LocalDate date);

    @Select("SELECT * FROM popular_activity WHERE date = #{date} ORDER BY hot_score DESC LIMIT #{limit}")
    List<PopularActivity> findTopHotActivities(@Param("date") LocalDate date, @Param("limit") int limit);

    @Update("UPDATE popular_activity " +
            "SET view_count = view_count + 1, " +
            "    hot_score = view_count * 0.3 + apply_count * 0.5 + share_count * 0.2, " +
            "    update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND date = #{date}")
    int updateViewCount(@Param("activityId") Long activityId, @Param("date") LocalDate date);

    @Update("UPDATE popular_activity " +
            "SET apply_count = apply_count + 1, " +
            "    hot_score = view_count * 0.3 + apply_count * 0.5 + share_count * 0.2, " +
            "    update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND date = #{date}")
    int updateApplyCount(@Param("activityId") Long activityId, @Param("date") LocalDate date);

    @Update("UPDATE popular_activity " +
            "SET share_count = share_count + 1, " +
            "    hot_score = view_count * 0.3 + apply_count * 0.5 + share_count * 0.2, " +
            "    update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND date = #{date}")
    int updateShareCount(@Param("activityId") Long activityId, @Param("date") LocalDate date);
}
