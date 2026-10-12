package com.volunteer.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.attendance.entity.Attendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    @Select("SELECT COALESCE(SUM(duration_hours), 0) " +
            "FROM attendance " +
            "WHERE user_id = #{userId} AND status = 'APPROVED' AND deleted = 0")
    BigDecimal sumApprovedDurationHours(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT activity_id) " +
            "FROM attendance " +
            "WHERE user_id = #{userId} AND status = 'APPROVED' AND deleted = 0")
    int countApprovedActivities(@Param("userId") Long userId);
}
