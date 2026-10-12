package com.volunteer.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.auth.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper(R-01 实施)
 * 提供按角色加载权限码的查询。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 根据角色 id 集合查询其下的权限码列表
     * <p>使用 &lt;script&gt; + foreach 展开 IN 集合,避免 #{roleIds} 被绑定成单值。</p>
     */
    @Select("<script>" +
            "SELECT DISTINCT p.permission_code " +
            "FROM sys_role_permission rp " +
            "JOIN sys_permission p ON rp.permission_id = p.id " +
            "WHERE p.status = 1 " +
            "  AND rp.role_id IN " +
            "  <foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>" +
            "    #{rid} " +
            "  </foreach>" +
            "</script>")
    List<String> selectPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);
}
