package com.volunteer.activity.mapper;

import com.volunteer.activity.entity.ActivityChangeLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActivityChangeLogMapper extends BaseMapper<ActivityChangeLog> {

    @Select("SELECT * FROM activity_change_log WHERE activity_id = #{activityId} ORDER BY create_time DESC, id DESC")
    List<ActivityChangeLog> selectByActivityId(@Param("activityId") Long activityId);
}
