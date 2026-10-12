package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.dto.UserRegistrationDTO;
import com.volunteer.activity.entity.ActivityRegistration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityRegistrationMapper extends BaseMapper<ActivityRegistration> {

    @Select("<script>" +
            "SELECT " +
            "    ar.id AS id, " +
            "    ar.activity_id AS activityId, " +
            "    a.title AS activityTitle, " +
            "    a.cover_image AS activityCover, " +
            "    a.start_time AS activityStartTime, " +
            "    a.location AS activityLocation, " +
            "    ar.status AS status, " +
            "    ar.create_time AS createdAt, " +
            "    att.duration_hours AS hours, " +
            "    att.check_in_time AS checkedInTime, " +
            "    att.check_out_time AS checkedOutTime " +
            "FROM activity_registration ar " +
            "LEFT JOIN activity a ON ar.activity_id = a.id " +
            "LEFT JOIN attendance att ON ar.activity_id = att.activity_id AND ar.user_id = att.user_id " +
            "WHERE ar.user_id = #{userId} " +
            "  <if test='status != null and status != \"\"'> " +
            "    AND ar.status = #{status} " +
            "  </if> " +
            "ORDER BY ar.create_time DESC" +
            "</script>")
    Page<UserRegistrationDTO> selectUserRegistrationsPage(
            Page<UserRegistrationDTO> page,
            @Param("userId") Long userId,
            @Param("status") String status
    );

    @Select("SELECT " +
            "    ar.id AS id, " +
            "    ar.activity_id AS activityId, " +
            "    a.title AS activityTitle, " +
            "    a.cover_image AS activityCover, " +
            "    a.start_time AS activityStartTime, " +
            "    a.location AS activityLocation, " +
            "    ar.status AS status, " +
            "    ar.create_time AS createdAt, " +
            "    att.duration_hours AS hours, " +
            "    att.check_in_time AS checkedInTime, " +
            "    att.check_out_time AS checkedOutTime " +
            "FROM activity_registration ar " +
            "LEFT JOIN activity a ON ar.activity_id = a.id " +
            "LEFT JOIN attendance att ON ar.activity_id = att.activity_id AND ar.user_id = att.user_id " +
            "WHERE ar.activity_id = #{activityId} " +
            "  AND ar.user_id = #{userId} " +
            "LIMIT 1")
    UserRegistrationDTO selectUserRegistration(
            @Param("activityId") Long activityId,
            @Param("userId") Long userId
    );
}
