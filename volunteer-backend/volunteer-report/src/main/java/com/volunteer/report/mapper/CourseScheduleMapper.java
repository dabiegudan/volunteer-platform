package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CourseSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface CourseScheduleMapper extends BaseMapper<CourseSchedule> {

    @Select("SELECT * FROM course_schedule WHERE user_id = #{userId} AND status = 1 " +
            "ORDER BY day_of_week ASC, period ASC, start_week ASC")
    List<CourseSchedule> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM course_schedule " +
            "WHERE user_id = #{userId} AND status = 1 " +
            "  AND start_week <= #{week} AND end_week >= #{week} " +
            "ORDER BY day_of_week ASC, period ASC")
    List<CourseSchedule> findByUserIdAndWeek(@Param("userId") Long userId, @Param("week") Integer week);

    @Select("SELECT * FROM course_schedule " +
            "WHERE user_id = #{userId} AND status = 1 " +
            "  AND day_of_week = #{dayOfWeek} AND period = #{period}")
    List<CourseSchedule> findByUserIdAndDayAndPeriod(@Param("userId") Long userId,
                                                      @Param("dayOfWeek") Integer dayOfWeek,
                                                      @Param("period") Integer period);

    @Select("SELECT * FROM course_schedule " +
            "WHERE user_id = #{userId} AND status = 1 AND day_of_week = #{dayOfWeek} " +
            "ORDER BY period ASC")
    List<CourseSchedule> findByUserIdAndDay(@Param("userId") Long userId, @Param("dayOfWeek") Integer dayOfWeek);

    @Delete("DELETE FROM course_schedule WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM course_schedule WHERE user_id = #{userId} AND status = 1")
    int countByUserId(@Param("userId") Long userId);
}
