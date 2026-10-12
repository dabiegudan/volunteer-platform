package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CourseImportLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseImportLogMapper extends BaseMapper<CourseImportLog> {

    @Select("SELECT * FROM course_import_log WHERE user_id = #{userId} ORDER BY import_time DESC")
    List<CourseImportLog> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM course_import_log WHERE user_id = #{userId} ORDER BY import_time DESC")
    List<CourseImportLog> findByUserIdOrderByTime(@Param("userId") Long userId);
}
