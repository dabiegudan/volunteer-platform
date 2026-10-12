package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.UserTaskRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTaskRecordMapper extends BaseMapper<UserTaskRecord> {

    @Select("SELECT * FROM user_task_record WHERE user_id = #{userId} ORDER BY date DESC, id DESC")
    List<UserTaskRecord> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_task_record WHERE user_id = #{userId} AND date = #{date} ORDER BY id ASC")
    List<UserTaskRecord> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") String date);

    @Select("SELECT * FROM user_task_record " +
            "WHERE user_id = #{userId} AND task_id = #{taskId} AND date = #{date} LIMIT 1")
    UserTaskRecord findByUserIdAndTaskIdAndDate(@Param("userId") Long userId,
                                                @Param("taskId") Long taskId,
                                                @Param("date") String date);

    @Select("SELECT COUNT(*) FROM user_task_record " +
            "WHERE user_id = #{userId} AND date = #{date} AND status IN ('COMPLETED', 'REWARDED')")
    int countCompletedByUserIdAndDate(@Param("userId") Long userId, @Param("date") String date);

    @Select("SELECT COUNT(*) FROM user_task_record " +
            "WHERE user_id = #{userId} AND date = #{date} AND status = 'REWARDED'")
    int countRewardedByUserIdAndDate(@Param("userId") Long userId, @Param("date") String date);
}
