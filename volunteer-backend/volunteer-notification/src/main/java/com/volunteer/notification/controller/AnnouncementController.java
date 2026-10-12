package com.volunteer.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.notification.entity.SystemAnnouncement;
import com.volunteer.notification.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * #12 系统公告/全员广播 - 控制器
 */
@Tag(name = "系统公告")
@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
@Slf4j
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "分页查询公告")
    @GetMapping("/page")
    public Result<PageResult<SystemAnnouncement>> getAnnouncementPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type) {
        Page<SystemAnnouncement> page = announcementService.getAnnouncementPage(pageNum, pageSize, type);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取活跃公告列表(首页轮播)")
    @GetMapping("/active")
    public Result<List<SystemAnnouncement>> getActiveAnnouncements() {
        return Result.success(announcementService.getActiveAnnouncements());
    }

    @Operation(summary = "获取公告详情")
    @GetMapping("/{id}")
    public Result<SystemAnnouncement> getAnnouncementById(@PathVariable Long id) {
        return Result.success(announcementService.getAnnouncementById(id));
    }

    @Operation(summary = "发布公告(管理员)")
    @PostMapping
    @RequiresAccess("admin")
    public Result<SystemAnnouncement> createAnnouncement(@RequestBody SystemAnnouncement announcement,
                                                          @RequestAttribute("userId") Long userId) {
        announcement.setPublisherId(userId);
        return Result.success(announcementService.createAnnouncement(announcement));
    }

    @Operation(summary = "更新公告(管理员)")
    @PutMapping("/{id}")
    @RequiresAccess("admin")
    public Result<Void> updateAnnouncement(@PathVariable Long id, @RequestBody SystemAnnouncement announcement) {
        announcement.setId(id);
        announcementService.updateAnnouncement(announcement);
        return Result.success();
    }

    @Operation(summary = "删除公告(管理员)")
    @DeleteMapping("/{id}")
    @RequiresAccess("admin")
    public Result<Void> deleteAnnouncement(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return Result.success();
    }
}
