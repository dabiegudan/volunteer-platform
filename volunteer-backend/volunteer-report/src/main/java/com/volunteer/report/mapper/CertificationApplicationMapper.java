package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CertificationApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CertificationApplicationMapper extends BaseMapper<CertificationApplication> {

    @Select("SELECT * FROM certification_application WHERE user_id = #{userId} ORDER BY apply_time DESC")
    List<CertificationApplication> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM certification_application WHERE status = #{status} ORDER BY apply_time ASC")
    List<CertificationApplication> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM certification_application " +
            "WHERE user_id = #{userId} AND skill_id = #{skillId} " +
            "ORDER BY apply_time DESC")
    List<CertificationApplication> findByUserIdAndSkillId(@Param("userId") Long userId, @Param("skillId") Long skillId);

    @Select("SELECT * FROM certification_application WHERE status = 'PENDING' ORDER BY apply_time ASC")
    List<CertificationApplication> findPendingApplications();
}
