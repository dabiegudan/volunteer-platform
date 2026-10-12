package com.volunteer.auth.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.auth.entity.SysOrganization;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.auth.service.CollegeService;
import com.volunteer.auth.service.UserService;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "学院管理", description = "学院绑定、查询等接口")
@RestController
@RequestMapping("/api/college")
@RequiredArgsConstructor
public class CollegeController {

    private final UserService userService;
    private final CollegeService collegeService;

    @Operation(summary = "获取所有学院列表")
    @GetMapping("/list")
    public Result<List<SysOrganization>> getCollegeList() {
        return Result.success(collegeService.listActiveColleges());
    }

    @Operation(summary = "为用户绑定学院（管理员）")
    @PostMapping("/assign/{userId}/{collegeId}")
    @RequiresAccess("admin")
    public Result<Void> assignCollege(@PathVariable Long userId, @PathVariable Long collegeId) {
        userService.assignCollege(userId, collegeId);
        return Result.success();
    }

    @Operation(summary = "检查当前用户是否已绑定学院")
    @GetMapping("/check-bind")
    public Result<Boolean> checkCollegeBind(HttpServletRequest request) {
        SysUser currentUser = (SysUser) request.getAttribute("currentUser");
        boolean isBound = currentUser != null && currentUser.getCollegeId() != null;
        return Result.success(isBound);
    }
}
