package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.SkillCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkillCategoryMapper extends BaseMapper<SkillCategory> {

    @Select("SELECT * FROM skill_category WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<SkillCategory> findActiveCategories();
}
