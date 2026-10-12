package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    
    @Update("UPDATE activity " +
            "SET current_participants = current_participants + 1, " +
            "    update_time = NOW() " +
            "WHERE id = #{id} " +
            "AND (max_participants IS NULL OR current_participants < max_participants) " +
            "AND deleted = 0")
    int increaseParticipantsWithLock(@Param("id") Long id);
    
    @Update("UPDATE activity " +
            "SET current_participants = current_participants - 1, " +
            "    update_time = NOW() " +
            "WHERE id = #{id} " +
            "AND current_participants > 0 " +
            "AND deleted = 0")
    int decreaseParticipantsWithLock(@Param("id") Long id);
}
