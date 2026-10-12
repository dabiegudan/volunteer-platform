package com.volunteer.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.auth.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 根据用户 id 查询其拥有的角色 id 集合(R-01 实施)
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(Long userId);
}
