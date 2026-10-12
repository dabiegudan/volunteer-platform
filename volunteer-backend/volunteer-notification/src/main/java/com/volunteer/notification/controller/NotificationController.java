package com.volunteer.notification.controller;

import com.volunteer.notification.entity.UserMessage;
import com.volunteer.notification.service.NotificationService;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "消息中心")
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    
    private final NotificationService notificationService;
    
    @Operation(summary = "获取消息列表")
    @GetMapping("/page")
    public Result<PageResult<UserMessage>> getMessagePage(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return Result.success(PageResult.of(notificationService.getMessagePage(userId, pageNum, pageSize)));
    }
    
    @Operation(summary = "获取未读消息数量")
    @GetMapping("/unread/count")
    public Result<Long> getUnreadCount(@RequestAttribute("userId") Long userId) {
        return Result.success(notificationService.getUnreadCount(userId));
    }
    
    @Operation(summary = "标记消息为已读")
    @PostMapping("/{messageId}/read")
    public Result<Void> markAsRead(@PathVariable Long messageId, @RequestAttribute("userId") Long userId) {
        notificationService.markAsRead(messageId, userId);
        return Result.success();
    }
    
    @Operation(summary = "标记所有消息为已读")
    @PostMapping("/read/all")
    public Result<Void> markAllAsRead(@RequestAttribute("userId") Long userId) {
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/{messageId}")
    public Result<Void> deleteMessage(@PathVariable Long messageId, @RequestAttribute("userId") Long userId) {
        notificationService.deleteMessage(messageId, userId);
        return Result.success();
    }
}
