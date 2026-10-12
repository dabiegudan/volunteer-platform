package com.volunteer.report.controller;

import com.volunteer.common.result.Result;
import com.volunteer.report.entity.UserTodo;
import com.volunteer.report.service.UserTodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "待办事项管理")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Slf4j
public class TodoController {
    
    private final UserTodoService userTodoService;
    
    @Operation(summary = "获取待办列表")
    @GetMapping
    public Result<List<UserTodo>> getTodoList(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String status) {
        return Result.success(userTodoService.getUserTodos(userId, status));
    }
    
    @Operation(summary = "创建待办")
    @PostMapping
    public Result<UserTodo> createTodo(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        return Result.success(userTodoService.createTodo(userId, title));
    }
    
    @Operation(summary = "更新待办")
    @PutMapping("/{id}")
    public Result<UserTodo> updateTodo(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        return Result.success(userTodoService.updateTodo(userId, id, title));
    }
    
    @Operation(summary = "删除待办")
    @DeleteMapping("/completed")
    public Result<Void> clearCompleted(
            @RequestAttribute("userId") Long userId) {
        userTodoService.clearCompleted(userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTodo(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        userTodoService.deleteTodo(userId, id);
        return Result.success();
    }
    
    @Operation(summary = "切换待办完成状态")
    @PutMapping("/{id}/toggle")
    public Result<UserTodo> toggleTodo(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> request) {
        Boolean completed = request != null ? request.get("completed") : null;
        return Result.success(userTodoService.toggleTodo(userId, id, completed));
    }
}
