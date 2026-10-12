package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.ActivityPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActivityPreferenceMapper extends BaseMapper<ActivityPreference> {

    @Select("SELECT * FROM activity_preference WHERE user_id = #{userId} ORDER BY priority DESC, id ASC")
    List<ActivityPreference> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM activity_preference " +
            "WHERE user_id = #{userId} AND activity_type = #{activityType} LIMIT 1")
    ActivityPreference findByUserIdAndType(@Param("userId") Long userId, @Param("activityType") String activityType);
}
