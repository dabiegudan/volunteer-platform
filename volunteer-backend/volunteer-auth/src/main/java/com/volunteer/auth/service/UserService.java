package com.volunteer.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.auth.entity.SysRole;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.entity.SysUserRole;
import com.volunteer.auth.mapper.SysRoleMapper;
import com.volunteer.auth.mapper.SysUserMapper;
import com.volunteer.auth.mapper.SysUserRoleMapper;
import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 口令编码器统一由 {@code SecurityConfig#passwordEncoder()} 提供（strength = 12）。
     *
     * <p>历史实现在此处 {@code new BCryptPasswordEncoder()}，使用默认 strength = 10，
     * 与 {@code SecurityConfig} 中校验用的 strength = 12 不是同一个 Bean；
     * 结果是「管理员重置口令」与「登录校验」两套成本混用，且新建/重置的口令强度
     * 弱于设计值。改为注入后单一来源，强度固定为 12。</p>
     */
    private final BCryptPasswordEncoder passwordEncoder;
    
    public Page<SysUser> getUserPage(Integer pageNum, Integer pageSize, String username, String realName) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        if (username != null && !username.isEmpty()) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (realName != null && !realName.isEmpty()) {
            wrapper.like(SysUser::getRealName, realName);
        }
        
        wrapper.orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(page, wrapper);
    }
    
    public SysUser getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    @Transactional
    public SysUser createUser(SysUser user) {
        SysUser existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, user.getUsername())
        );
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }
    
    @Transactional
    public void updateUser(SysUser user) {
        SysUser existingUser = userMapper.selectById(user.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userMapper.updateById(user);
    }

    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("用户状态只能为0或1");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (password == null || password.length() < 8 || password.length() > 32) {
            throw new BusinessException("密码长度必须为8到32位");
        }
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.deleteById(id);
    }
    
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        
        SysUserRole userRole = userRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, roleId)
        );
        
        if (userRole == null) {
            userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }
    
    public List<SysRole> getUserRoles(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
        );

        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());

        return roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
        );
    }
    
    @Transactional
    public void assignCollege(Long userId, Long collegeId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        boolean isOrganizer = checkIsOrganizer(userId);
        if (isOrganizer && collegeId == null) {
            throw new BusinessException("组织者必须绑定学院");
        }
        
        user.setCollegeId(collegeId);
        userMapper.updateById(user);
    }
    
    public void checkOrganizerCollegeBind(SysUser user) {
        if (checkIsOrganizer(user.getId()) && user.getCollegeId() == null) {
            throw new BusinessException("请先联系管理员绑定学院后再进行操作");
        }
    }
    
    public boolean checkIsOrganizer(Long userId) {
        List<SysRole> roles = getUserRoles(userId);
        return roles.stream().anyMatch(role -> "ORGANIZER".equals(role.getRoleCode()));
    }
    
    public boolean checkIsAdmin(Long userId) {
        List<SysRole> roles = getUserRoles(userId);
        return roles.stream().anyMatch(role -> "ADMIN".equals(role.getRoleCode()));
    }
}
