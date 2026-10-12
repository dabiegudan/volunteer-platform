package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.UserSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserSkillMapper extends BaseMapper<UserSkill> {

    @Select("SELECT * FROM user_skill WHERE user_id = #{userId} ORDER BY id ASC")
    List<UserSkill> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_skill WHERE user_id = #{userId} AND status = #{status} ORDER BY id ASC")
    List<UserSkill> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM user_skill WHERE user_id = #{userId} AND skill_id = #{skillId} LIMIT 1")
    UserSkill findByUserIdAndSkillId(@Param("userId") Long userId, @Param("skillId") Long skillId);

    @Select("SELECT * FROM user_skill WHERE user_id = #{userId} AND status = 'APPROVED' ORDER BY proficiency DESC, id ASC")
    List<UserSkill> findApprovedSkillsByUserId(@Param("userId") Long userId);
}
