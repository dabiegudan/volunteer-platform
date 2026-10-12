package com.volunteer.auth.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.entity.SysRole;
import com.volunteer.auth.service.RoleService;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "分页查询角色")
    @GetMapping("/page")
    @RequiresAccess("admin")
    public Result<PageResult<SysRole>> getRolePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(PageResult.of(roleService.getRolePage(pageNum, pageSize, keyword)));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @RequiresAccess("admin")
    public Result<SysRole> createRole(@RequestBody SysRole role) {
        return Result.success(roleService.createRole(role));
    }

    @Operation(summary = "分配角色权限")
    @PutMapping("/{id}/permissions")
    @RequiresAccess("admin")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        roleService.assignPermissions(id, readPermissionIds(body));
        return Result.success();
    }

    @SuppressWarnings("unchecked")
    private List<Long> readPermissionIds(Map<String, Object> body) {
        if (body == null || body.get("permissionIds") == null) {
            return Collections.emptyList();
        }
        Object value = body.get("permissionIds");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(item -> Long.valueOf(String.valueOf(item)))
                    .toList();
        }
        return (List<Long>) value;
    }
}
