package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.SkillMatching;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkillMatchingMapper extends BaseMapper<SkillMatching> {

    @Select("SELECT * FROM skill_matching WHERE activity_id = #{activityId} ORDER BY importance DESC, id ASC")
    List<SkillMatching> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM skill_matching WHERE skill_id = #{skillId} ORDER BY importance DESC, id ASC")
    List<SkillMatching> findBySkillId(@Param("skillId") Long skillId);

    @Select("SELECT * FROM skill_matching " +
            "WHERE activity_id = #{activityId} AND skill_id = #{skillId} LIMIT 1")
    SkillMatching findByActivityIdAndSkillId(@Param("activityId") Long activityId, @Param("skillId") Long skillId);
}
