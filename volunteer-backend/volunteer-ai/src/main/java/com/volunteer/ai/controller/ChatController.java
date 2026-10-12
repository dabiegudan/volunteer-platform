package com.volunteer.ai.controller;

import com.volunteer.ai.service.ChatService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI智能助手", description = "智能问答、聊天对话接口")
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    
    @Operation(summary = "智能对话")
    @RequiresAccess(requireStudentId = true)
    @PostMapping
    public Result<String> chat(@Parameter(description = "用户消息") @RequestBody String message) {
        try {
            if (message == null || message.trim().isEmpty()) {
                return Result.error("消息内容不能为空");
            }
            String response = chatService.chat(message.trim());
            return Result.success(response);
        } catch (Exception e) {
            log.error("智能对话失败", e);
            return Result.error("智能对话失败");
        }
    }
    
    @Operation(summary = "带历史对话的智能问答")
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/history")
    public Result<String> chatWithHistory(@RequestBody Map<String, Object> request) {
        try {
            String message = (String) (request.get("message") != null ? request.get("message") : request.get("question"));
            @SuppressWarnings("unchecked")
            List<Map<String, String>> history = (List<Map<String, String>>) request.get("history");
            
            if (message == null || message.trim().isEmpty()) {
                return Result.error("消息内容不能为空");
            }
            
            String response = chatService.chatWithHistory(message.trim(), history);
            return Result.success(response);
        } catch (Exception e) {
            log.error("带历史对话的智能问答失败", e);
            return Result.error("带历史对话的智能问答失败");
        }
    }
    
    @Operation(summary = "获取常见问题列表")
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/faqs")
    public Result<Map<String, Object>> getFAQs() {
        try {
            Map<String, Object> faqs = chatService.getFAQs();
            return Result.success(faqs);
        } catch (Exception e) {
            log.error("获取常见问题失败", e);
            return Result.error("获取常见问题失败");
        }
    }
    
    @Operation(summary = "健康检查")
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/health")
    public Result<String> healthCheck() {
        return Result.success("AI服务运行正常");
    }
}
