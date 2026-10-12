package com.volunteer.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Team;
import com.volunteer.activity.entity.TeamMember;
import com.volunteer.activity.mapper.TeamMapper;
import com.volunteer.activity.mapper.TeamMemberMapper;
import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {
    
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    
    @Transactional
    public Team createTeam(String teamName, Long activityId, Long leaderId, Integer maxMembers) {
        Team team = new Team();
        team.setTeamName(teamName);
        team.setActivityId(activityId);
        team.setLeaderId(leaderId);
        team.setMaxMembers(maxMembers);
        team.setStatus("ACTIVE");
        teamMapper.insert(team);
        
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(leaderId);
        member.setStatus("JOINED");
        teamMemberMapper.insert(member);
        
        return team;
    }
    
    @Transactional
    public void joinTeam(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        
        if (!"ACTIVE".equals(team.getStatus())) {
            throw new BusinessException("队伍已解散");
        }
        
        TeamMember existing = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId)
        );
        
        if (existing != null) {
            throw new BusinessException("您已加入该队伍");
        }
        
        long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, "JOINED")
        );
        
        if (memberCount >= team.getMaxMembers()) {
            throw new BusinessException("队伍已满员");
        }
        
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setStatus("JOINED");
        teamMemberMapper.insert(member);
    }
    
    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        
        if (team.getLeaderId().equals(userId)) {
            throw new BusinessException("队长不能退出队伍，请先解散队伍");
        }
        
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId)
        );
        
        if (member == null) {
            throw new BusinessException("您不是该队伍成员");
        }
        
        teamMemberMapper.deleteById(member.getId());
    }
    
    @Transactional
    public void disbandTeam(Long teamId, Long userId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException("队伍不存在");
        }
        
        if (!team.getLeaderId().equals(userId)) {
            throw new BusinessException("只有队长可以解散队伍");
        }
        
        team.setStatus("DISBANDED");
        teamMapper.updateById(team);
        
        teamMemberMapper.delete(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
        );
    }
    
    public List<Team> getActivityTeams(Long activityId) {
        return teamMapper.selectList(
                new LambdaQueryWrapper<Team>()
                        .eq(Team::getActivityId, activityId)
                        .eq(Team::getStatus, "ACTIVE")
        );
    }
    
    public List<TeamMember> getTeamMembers(Long teamId) {
        return teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getStatus, "JOINED")
        );
    }
}
