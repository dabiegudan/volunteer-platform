package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.ImprovementSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImprovementSuggestionMapper extends BaseMapper<ImprovementSuggestion> {

    @Select("SELECT * FROM improvement_suggestion WHERE activity_id = #{activityId} ORDER BY priority DESC, id ASC")
    List<ImprovementSuggestion> findByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT * FROM improvement_suggestion WHERE severity = #{severity} ORDER BY priority DESC, id ASC")
    List<ImprovementSuggestion> findBySeverity(@Param("severity") String severity);

    @Select("SELECT * FROM improvement_suggestion WHERE status = #{status} ORDER BY priority DESC, id ASC")
    List<ImprovementSuggestion> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM improvement_suggestion " +
            "WHERE activity_id = #{activityId} AND status = #{status} " +
            "ORDER BY priority DESC, id ASC")
    List<ImprovementSuggestion> findByActivityIdAndStatus(@Param("activityId") Long activityId, @Param("status") String status);

    @Select("SELECT * FROM improvement_suggestion " +
            "WHERE status = 'PENDING' AND severity = #{severity} " +
            "ORDER BY priority DESC, id ASC")
    List<ImprovementSuggestion> findPendingBySeverity(@Param("severity") String severity);
}
