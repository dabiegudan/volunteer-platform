package com.volunteer.activity.controller;

import com.volunteer.activity.entity.Team;
import com.volunteer.activity.entity.TeamMember;
import com.volunteer.activity.service.TeamService;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "组队管理")
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
@Slf4j
public class TeamController {
    
    private final TeamService teamService;
    
    @Operation(summary = "创建队伍")
    @PostMapping
    @RequiresAccess(requireStudentId = true)
    public Result<Team> createTeam(
            @RequestBody Map<String, Object> params,
            @RequestAttribute("userId") Long userId
    ) {
        String teamName = (String) params.get("teamName");
        Long activityId = Long.valueOf(params.get("activityId").toString());
        Integer maxMembers = params.get("maxMembers") != null ? Integer.valueOf(params.get("maxMembers").toString()) : 10;
        return Result.success(teamService.createTeam(teamName, activityId, userId, maxMembers));
    }
    
    @Operation(summary = "加入队伍")
    @PostMapping("/{teamId}/join")
    public Result<Void> joinTeam(@PathVariable Long teamId, @RequestAttribute("userId") Long userId) {
        teamService.joinTeam(teamId, userId);
        return Result.success();
    }
    
    @Operation(summary = "退出队伍")
    @PostMapping("/{teamId}/leave")
    public Result<Void> leaveTeam(@PathVariable Long teamId, @RequestAttribute("userId") Long userId) {
        teamService.leaveTeam(teamId, userId);
        return Result.success();
    }
    
    @Operation(summary = "解散队伍")
    @PostMapping("/{teamId}/disband")
    public Result<Void> disbandTeam(@PathVariable Long teamId, @RequestAttribute("userId") Long userId) {
        teamService.disbandTeam(teamId, userId);
        return Result.success();
    }
    
    @Operation(summary = "获取活动队伍列表")
    @GetMapping("/activity/{activityId}")
    public Result<List<Team>> getActivityTeams(@PathVariable Long activityId) {
        return Result.success(teamService.getActivityTeams(activityId));
    }
    
    @Operation(summary = "获取队伍成员")
    @GetMapping("/{teamId}/members")
    public Result<List<TeamMember>> getTeamMembers(@PathVariable Long teamId) {
        return Result.success(teamService.getTeamMembers(teamId));
    }
}
