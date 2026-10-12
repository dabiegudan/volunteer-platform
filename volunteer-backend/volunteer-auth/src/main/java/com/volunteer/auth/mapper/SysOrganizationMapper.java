package com.volunteer.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.auth.entity.SysOrganization;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织(学院) Mapper(R-06 实施)
 * <p>sys_organization 表的轻量访问入口,主要服务于前端下拉。</p>
 */
@Mapper
public interface SysOrganizationMapper extends BaseMapper<SysOrganization> {
}
