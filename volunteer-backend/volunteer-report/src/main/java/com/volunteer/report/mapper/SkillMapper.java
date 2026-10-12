package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkillMapper extends BaseMapper<Skill> {

    @Select("SELECT * FROM skill WHERE category_id = #{categoryId} AND status = 1 ORDER BY id ASC")
    List<Skill> findByCategoryId(@Param("categoryId") Long categoryId);

    @Select("SELECT * FROM skill WHERE status = 1 ORDER BY id ASC")
    List<Skill> findActiveSkills();

    @Select("SELECT * FROM skill " +
            "WHERE status = 1 AND (name LIKE CONCAT('%', #{keyword}, '%') " +
            "                     OR description LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY id ASC")
    List<Skill> searchByKeyword(@Param("keyword") String keyword);
}
