package com.volunteer.auth.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.entity.SysRole;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    @Operation(summary = "分页查询用户列表")
    @GetMapping("/page")
    @RequiresAccess("admin")
    public Result<PageResult<SysUser>> getUserPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName
    ) {
        return Result.success(PageResult.of(userService.getUserPage(pageNum, pageSize, username, realName)));
    }
    
    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    @RequiresAccess("admin")
    public Result<SysUser> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }
    
    @Operation(summary = "创建用户")
    @PostMapping
    @RequiresAccess("admin")
    public Result<SysUser> createUser(@RequestBody SysUser user) {
        return Result.success(userService.createUser(user));
    }
    
    @Operation(summary = "更新用户")
    @PutMapping
    @RequiresAccess("admin")
    public Result<Void> updateUser(@RequestBody SysUser user) {
        userService.updateUser(user);
        return Result.success();
    }

    @Operation(summary = "修改用户状态")
    @PutMapping("/{id}/status")
    @RequiresAccess("admin")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status")));
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/password")
    @RequiresAccess("admin")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object password = body.get("password");
        userService.resetPassword(id, password == null ? null : String.valueOf(password));
        return Result.success();
    }
    
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @RequiresAccess("admin")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }
    
    @Operation(summary = "分配角色")
    @PostMapping("/{userId}/role/{roleId}")
    @RequiresAccess("admin")
    public Result<Void> assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        userService.assignRole(userId, roleId);
        return Result.success();
    }
    
    @Operation(summary = "获取用户角色")
    @GetMapping("/{userId}/roles")
    @RequiresAccess("admin")
    public Result<List<SysRole>> getUserRoles(@PathVariable Long userId) {
        return Result.success(userService.getUserRoles(userId));
    }
}
