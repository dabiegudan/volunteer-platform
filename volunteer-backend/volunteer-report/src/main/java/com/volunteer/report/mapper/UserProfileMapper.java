package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    @Select("SELECT * FROM user_profile WHERE user_id = #{userId} LIMIT 1")
    UserProfile findByUserId(@Param("userId") Long userId);

    @Update("UPDATE user_profile SET availability_score = #{score}, update_time = NOW() WHERE user_id = #{userId}")
    int updateAvailabilityScore(@Param("userId") Long userId, @Param("score") Integer score);

    @Update("UPDATE user_profile SET reliability_score = #{score}, update_time = NOW() WHERE user_id = #{userId}")
    int updateReliabilityScore(@Param("userId") Long userId, @Param("score") Integer score);
}
