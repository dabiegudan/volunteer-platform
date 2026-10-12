package com.volunteer.report.service;

import com.volunteer.report.entity.OrganizationStatistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationReportService {
    
    private final JdbcTemplate jdbcTemplate;
    
    public List<OrganizationStatistic> getOrganizationStatistics() {
        String sql = """
            SELECT 
                o.id as organizationId,
                o.organization_name as organizationName,
                COALESCE(u.volunteer_count, 0) as volunteerCount,
                COALESCE(a.activity_count, 0) as activityCount,
                COALESCE(d.total_duration, 0) as totalDuration,
                CURRENT_TIMESTAMP as updateTime
            FROM sys_organization o
            LEFT JOIN (
                SELECT org_id, COUNT(DISTINCT user_id) as volunteer_count
                FROM (
                    SELECT id as user_id, organization_id as org_id
                    FROM sys_user
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as user_id, college_id as org_id
                    FROM sys_user
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) user_org
                GROUP BY org_id
            ) u ON u.org_id = o.id
            LEFT JOIN (
                SELECT org_id, COUNT(DISTINCT activity_id) as activity_count
                FROM (
                    SELECT id as activity_id, organization_id as org_id
                    FROM activity
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as activity_id, college_id as org_id
                    FROM activity
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) activity_org
                GROUP BY org_id
            ) a ON a.org_id = o.id
            LEFT JOIN (
                SELECT user_org.org_id,
                       SUM(COALESCE(ud.adjusted_duration, ud.original_duration, 0)) as total_duration
                FROM (
                    SELECT id as user_id, organization_id as org_id
                    FROM sys_user
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as user_id, college_id as org_id
                    FROM sys_user
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) user_org
                JOIN user_duration ud ON ud.user_id = user_org.user_id AND ud.status = 'APPROVED'
                GROUP BY user_org.org_id
            ) d ON d.org_id = o.id
            ORDER BY totalDuration DESC
            """;
        
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(OrganizationStatistic.class));
    }
    
    public OrganizationStatistic getOrganizationStatistic(Long organizationId) {
        String sql = """
            SELECT 
                o.id as organizationId,
                o.organization_name as organizationName,
                COALESCE(u.volunteer_count, 0) as volunteerCount,
                COALESCE(a.activity_count, 0) as activityCount,
                COALESCE(d.total_duration, 0) as totalDuration,
                CURRENT_TIMESTAMP as updateTime
            FROM sys_organization o
            LEFT JOIN (
                SELECT org_id, COUNT(DISTINCT user_id) as volunteer_count
                FROM (
                    SELECT id as user_id, organization_id as org_id
                    FROM sys_user
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as user_id, college_id as org_id
                    FROM sys_user
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) user_org
                GROUP BY org_id
            ) u ON u.org_id = o.id
            LEFT JOIN (
                SELECT org_id, COUNT(DISTINCT activity_id) as activity_count
                FROM (
                    SELECT id as activity_id, organization_id as org_id
                    FROM activity
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as activity_id, college_id as org_id
                    FROM activity
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) activity_org
                GROUP BY org_id
            ) a ON a.org_id = o.id
            LEFT JOIN (
                SELECT user_org.org_id,
                       SUM(COALESCE(ud.adjusted_duration, ud.original_duration, 0)) as total_duration
                FROM (
                    SELECT id as user_id, organization_id as org_id
                    FROM sys_user
                    WHERE organization_id IS NOT NULL AND deleted = 0
                    UNION
                    SELECT id as user_id, college_id as org_id
                    FROM sys_user
                    WHERE college_id IS NOT NULL AND deleted = 0
                ) user_org
                JOIN user_duration ud ON ud.user_id = user_org.user_id AND ud.status = 'APPROVED'
                GROUP BY user_org.org_id
            ) d ON d.org_id = o.id
            WHERE o.id = ?
            """;
        
        List<OrganizationStatistic> results = jdbcTemplate.query(sql, 
                new BeanPropertyRowMapper<>(OrganizationStatistic.class), organizationId);
        
        return results.isEmpty() ? null : results.get(0);
    }
    
    public Map<String, Object> getCollegeStatistics(String collegeName) {
        String sql = """
            SELECT 
                (
                    SELECT COUNT(DISTINCT u.id)
                    FROM sys_user u
                    WHERE u.deleted = 0
                      AND (u.college_name = ? OR u.college_id IN (
                          SELECT id FROM sys_organization WHERE organization_name = ?
                      ))
                ) as totalUsers,
                (
                    SELECT COUNT(DISTINCT a.id)
                    FROM activity a
                    WHERE a.deleted = 0
                      AND a.college_id IN (
                          SELECT id FROM sys_organization WHERE organization_name = ?
                      )
                ) as totalActivities,
                (
                    SELECT COUNT(DISTINCT ar.id)
                    FROM activity_registration ar
                    JOIN sys_user u ON u.id = ar.user_id
                    WHERE u.deleted = 0
                      AND (u.college_name = ? OR u.college_id IN (
                          SELECT id FROM sys_organization WHERE organization_name = ?
                      ))
                ) as totalRegistrations,
                (
                    SELECT COALESCE(SUM(COALESCE(ud.adjusted_duration, ud.original_duration, 0)), 0)
                    FROM user_duration ud
                    JOIN sys_user u ON u.id = ud.user_id
                    WHERE ud.status = 'APPROVED'
                      AND u.deleted = 0
                      AND (u.college_name = ? OR u.college_id IN (
                          SELECT id FROM sys_organization WHERE organization_name = ?
                      ))
                ) as totalDuration,
                (
                    SELECT AVG(COALESCE(ud.adjusted_duration, ud.original_duration, 0))
                    FROM user_duration ud
                    JOIN sys_user u ON u.id = ud.user_id
                    WHERE ud.status = 'APPROVED'
                      AND u.deleted = 0
                      AND (u.college_name = ? OR u.college_id IN (
                          SELECT id FROM sys_organization WHERE organization_name = ?
                      ))
                ) as avgDurationPerUser
            """;
        
        return jdbcTemplate.queryForMap(sql,
                collegeName, collegeName,
                collegeName,
                collegeName, collegeName,
                collegeName, collegeName,
                collegeName, collegeName);
    }
    
    public List<Map<String, Object>> getAllCollegeStatistics() {
        String sql = """
            SELECT 
                o.organization_name as collegeName,
                COALESCE(u.total_users, 0) as totalUsers,
                COALESCE(a.total_activities, 0) as totalActivities,
                COALESCE(r.total_registrations, 0) as totalRegistrations,
                COALESCE(d.total_duration, 0) as totalDuration,
                d.avg_duration as avgDurationPerUser
            FROM sys_organization o
            LEFT JOIN (
                SELECT college_id, COUNT(DISTINCT id) as total_users
                FROM sys_user
                WHERE deleted = 0 AND college_id IS NOT NULL
                GROUP BY college_id
            ) u ON u.college_id = o.id
            LEFT JOIN (
                SELECT college_id, COUNT(DISTINCT id) as total_activities
                FROM activity
                WHERE deleted = 0 AND college_id IS NOT NULL
                GROUP BY college_id
            ) a ON a.college_id = o.id
            LEFT JOIN (
                SELECT u.college_id, COUNT(DISTINCT ar.id) as total_registrations
                FROM activity_registration ar
                JOIN sys_user u ON u.id = ar.user_id
                WHERE u.deleted = 0 AND u.college_id IS NOT NULL
                GROUP BY u.college_id
            ) r ON r.college_id = o.id
            LEFT JOIN (
                SELECT u.college_id,
                       SUM(COALESCE(ud.adjusted_duration, ud.original_duration, 0)) as total_duration,
                       AVG(COALESCE(ud.adjusted_duration, ud.original_duration, 0)) as avg_duration
                FROM user_duration ud
                JOIN sys_user u ON u.id = ud.user_id
                WHERE ud.status = 'APPROVED'
                  AND u.deleted = 0
                  AND u.college_id IS NOT NULL
                GROUP BY u.college_id
            ) d ON d.college_id = o.id
            WHERE o.organization_type = 'COLLEGE'
            ORDER BY totalDuration DESC
            """;
        
        return jdbcTemplate.queryForList(sql);
    }
}
