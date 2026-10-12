package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.UserMedal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMedalMapper extends BaseMapper<UserMedal> {

    @Select("SELECT * FROM user_medal WHERE user_id = #{userId} ORDER BY id ASC")
    List<UserMedal> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_medal WHERE user_id = #{userId} AND status = #{status} ORDER BY id ASC")
    List<UserMedal> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM user_medal WHERE user_id = #{userId} AND medal_id = #{medalId} LIMIT 1")
    UserMedal findByUserIdAndMedalId(@Param("userId") Long userId, @Param("medalId") Long medalId);

    @Select("SELECT COUNT(*) FROM user_medal WHERE user_id = #{userId} AND status = #{status}")
    int countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}
