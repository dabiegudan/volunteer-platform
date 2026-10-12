package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.TimeSlotConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TimeSlotConfigMapper extends BaseMapper<TimeSlotConfig> {

    @Select("SELECT * FROM time_slot_config WHERE status = 1 ORDER BY sort_order ASC, period ASC")
    List<TimeSlotConfig> findActiveSlots();

    @Select("SELECT * FROM time_slot_config WHERE period = #{period} LIMIT 1")
    TimeSlotConfig findByPeriod(@Param("period") Integer period);
}
