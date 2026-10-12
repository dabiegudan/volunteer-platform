package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.Medal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedalMapper extends BaseMapper<Medal> {

    @Select("SELECT * FROM medal WHERE category = #{category} ORDER BY sort_order ASC, id ASC")
    List<Medal> findByCategory(@Param("category") String category);

    @Select("SELECT * FROM medal WHERE rarity = #{rarity} ORDER BY sort_order ASC, id ASC")
    List<Medal> findByRarity(@Param("rarity") String rarity);

    @Select("SELECT * FROM medal WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<Medal> findActiveMedals();
}
