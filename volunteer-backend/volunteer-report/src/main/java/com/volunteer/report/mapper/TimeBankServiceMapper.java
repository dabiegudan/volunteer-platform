package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.TimeBankServiceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TimeBankServiceMapper extends BaseMapper<TimeBankServiceEntity> {
    
    @Update("UPDATE time_bank_service " +
            "SET available_count = available_count - 1 " +
            "WHERE id = #{id} AND available_count > 0")
    int decreaseAvailableCount(@Param("id") Long id);
}