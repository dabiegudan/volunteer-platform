package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.DailyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DailyTaskMapper extends BaseMapper<DailyTask> {

    @Select("SELECT * FROM daily_task WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<DailyTask> findActiveTasks();

    @Select("SELECT * FROM daily_task WHERE type = #{type} ORDER BY sort_order ASC, id ASC")
    List<DailyTask> findByType(@Param("type") String type);
}
