package com.volunteer.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.attendance.entity.AttendanceCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到凭据绑定 Mapper。
 *
 * <p>仅使用 MyBatis-Plus 的 Wrapper 查询，无自定义 SQL（与模块内其它 Mapper 一致，
 * 全注解、无 XML 映射文件）。</p>
 */
@Mapper
public interface AttendanceCredentialMapper extends BaseMapper<AttendanceCredential> {
}
