package com.volunteer.report.service;

import com.volunteer.common.enums.ColorCode;
import com.volunteer.common.enums.WeekType;
import com.volunteer.report.entity.*;
import com.volunteer.report.mapper.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    
    private final CourseScheduleMapper scheduleMapper;
    private final TimeSlotConfigMapper timeSlotMapper;
    private final WeekConfigMapper weekConfigMapper;
    private final CourseImportLogMapper importLogMapper;
    private final ActivityConflictCheckMapper conflictMapper;
    
    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] PERIOD_NAMES = {"第1节", "第2节", "第3节", "第4节", "第5节"};
    
    public List<TimeSlotConfig> getTimeSlots() {
        return timeSlotMapper.findActiveSlots();
    }
    
    public WeekConfig getCurrentSemester() {
        return weekConfigMapper.findActiveSemester();
    }
    
    public List<CourseSchedule> getUserSchedule(Long userId) {
        return scheduleMapper.findByUserId(userId);
    }
    
    public List<CourseSchedule> getUserScheduleByWeek(Long userId, Integer week) {
        return scheduleMapper.findByUserIdAndWeek(userId, week);
    }
    
    @Transactional
    public CourseSchedule addCourse(Long userId, CourseSchedule course) {
        validateCourse(course);
        
        course.setUserId(userId);
        course.setStatus(1);
        
        if (course.getStartWeek() == null) course.setStartWeek(1);
        if (course.getEndWeek() == null) course.setEndWeek(16);
        if (course.getWeekType() == null) course.setWeekType(WeekType.EVERY.getCode());

        String weekType = course.getWeekType();
        course.setColorCode(WeekType.EVERY.getCode().equals(weekType) ? ColorCode.RED.getCode() : ColorCode.YELLOW.getCode());
        
        scheduleMapper.insert(course);
        log.info("用户 {} 添加课程：{}", userId, course.getCourseName());
        return course;
    }
    
    private void validateCourse(CourseSchedule course) {
        if (course.getDayOfWeek() < 1 || course.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("星期几必须在1-7之间");
        }
        if (course.getPeriod() < 1 || course.getPeriod() > 5) {
            throw new IllegalArgumentException("节次必须在1-5之间");
        }
        if (course.getStartWeek() != null && course.getEndWeek() != null) {
            if (course.getStartWeek() > course.getEndWeek()) {
                throw new IllegalArgumentException("起始周不能大于结束周");
            }
        }
    }
    
    @Transactional
    public CourseSchedule updateCourse(Long userId, Long courseId, CourseSchedule updates) {
        CourseSchedule existing = scheduleMapper.selectById(courseId);
        if (existing == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        
        if (!Objects.equals(existing.getUserId(), userId)) {
            throw new IllegalArgumentException("无权修改该课程");
        }

        if (updates.getCourseName() != null) existing.setCourseName(updates.getCourseName());
        if (updates.getCourseCode() != null) existing.setCourseCode(updates.getCourseCode());
        if (updates.getTeacherName() != null) existing.setTeacherName(updates.getTeacherName());
        if (updates.getClassroom() != null) existing.setClassroom(updates.getClassroom());
        if (updates.getDayOfWeek() != null) existing.setDayOfWeek(updates.getDayOfWeek());
        if (updates.getPeriod() != null) existing.setPeriod(updates.getPeriod());
        if (updates.getStartWeek() != null) existing.setStartWeek(updates.getStartWeek());
        if (updates.getEndWeek() != null) existing.setEndWeek(updates.getEndWeek());
        if (updates.getWeekType() != null) {
            existing.setWeekType(updates.getWeekType());
            existing.setColorCode(WeekType.EVERY.getCode().equals(updates.getWeekType()) ? ColorCode.RED.getCode() : ColorCode.YELLOW.getCode());
        }
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
        
        validateCourse(existing);
        scheduleMapper.updateById(existing);
        return existing;
    }
    
    @Transactional
    public void deleteCourse(Long userId, Long courseId) {
        CourseSchedule course = scheduleMapper.selectById(courseId);
        if (course != null) {
            if (!Objects.equals(course.getUserId(), userId)) {
                throw new IllegalArgumentException("无权删除该课程");
            }
            course.setStatus(0);
            scheduleMapper.updateById(course);
        }
    }
    
    @Transactional
    public void deleteAllCourses(Long userId) {
        scheduleMapper.deleteByUserId(userId);
    }
    
    public Map<String, Object> getScheduleGrid(Long userId, Integer week) {
        List<CourseSchedule> courses = scheduleMapper.findByUserId(userId);
        List<TimeSlotConfig> timeSlots = timeSlotMapper.findActiveSlots();
        
        Map<Integer, Map<Integer, Map<String, Object>>> grid = new LinkedHashMap<>();
        
        for (int day = 1; day <= 7; day++) {
            Map<Integer, Map<String, Object>> dayGrid = new LinkedHashMap<>();
            for (int period = 1; period <= 5; period++) {
                Map<String, Object> cell = new HashMap<>();
                cell.put("dayOfWeek", day);
                cell.put("dayName", DAY_NAMES[day - 1]);
                cell.put("period", period);
                cell.put("periodName", PERIOD_NAMES[period - 1]);
                cell.put("timeSlot", getTimeSlotInfo(timeSlots, period));
                cell.put("course", null);
                cell.put("color", ColorCode.EMPTY.getCode());
                dayGrid.put(period, cell);
            }
            grid.put(day, dayGrid);
        }
        
        for (CourseSchedule course : courses) {
            if (isCourseActiveInWeek(course, week)) {
                Map<String, Object> courseInfo = new HashMap<>();
                courseInfo.put("id", course.getId());
                courseInfo.put("courseName", course.getCourseName());
                courseInfo.put("courseCode", course.getCourseCode());
                courseInfo.put("teacherName", course.getTeacherName());
                courseInfo.put("classroom", course.getClassroom());
                courseInfo.put("weekType", course.getWeekType());
                courseInfo.put("notes", course.getNotes());
                
                if (grid.containsKey(course.getDayOfWeek()) && 
                    grid.get(course.getDayOfWeek()).containsKey(course.getPeriod())) {
                    Map<String, Object> cell = grid.get(course.getDayOfWeek()).get(course.getPeriod());
                    cell.put("course", courseInfo);
                    cell.put("color", course.getColorCode());
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("week", week);
        result.put("grid", grid);
        result.put("dayNames", DAY_NAMES);
        result.put("periodNames", PERIOD_NAMES);
        result.put("timeSlots", timeSlots.stream().map(this::convertTimeSlot).collect(Collectors.toList()));
        
        return result;
    }
    
    private boolean isCourseActiveInWeek(CourseSchedule course, Integer week) {
        if (week == null) return true;
        
        if (week < course.getStartWeek() || week > course.getEndWeek()) {
            return false;
        }
        
        String weekType = course.getWeekType();
        if (WeekType.EVERY.getCode().equals(weekType)) {
            return true;
        } else if (WeekType.SINGLE.getCode().equals(weekType)) {
            return week % 2 == 1;
        } else if (WeekType.DOUBLE.getCode().equals(weekType)) {
            return week % 2 == 0;
        }

        return true;
    }
    
    private Map<String, Object> getTimeSlotInfo(List<TimeSlotConfig> slots, int period) {
        for (TimeSlotConfig slot : slots) {
            if (slot.getPeriod() == period) {
                Map<String, Object> info = new HashMap<>();
                info.put("startTime", slot.getStartTime());
                info.put("endTime", slot.getEndTime());
                info.put("duration", slot.getDurationMinutes());
                return info;
            }
        }
        return null;
    }
    
    private Map<String, Object> convertTimeSlot(TimeSlotConfig slot) {
        Map<String, Object> info = new HashMap<>();
        info.put("period", slot.getPeriod());
        info.put("startTime", slot.getStartTime());
        info.put("endTime", slot.getEndTime());
        info.put("duration", slot.getDurationMinutes());
        return info;
    }
    
    @Transactional
    public CourseImportLog importCourses(Long userId, List<CourseSchedule> courses, String importType, String fileName) {
        CourseImportLog logEntry = new CourseImportLog();
        logEntry.setUserId(userId);
        logEntry.setImportType(importType);
        logEntry.setFileName(fileName);
        logEntry.setTotalRecords(courses.size());
        logEntry.setImportTime(LocalDateTime.now());
        
        int successCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (CourseSchedule course : courses) {
            try {
                validateCourse(course);
                course.setUserId(userId);
                course.setStatus(1);
                if (WeekType.EVERY.getCode().equals(course.getWeekType())) {
                    course.setColorCode(ColorCode.RED.getCode());
                } else {
                    course.setColorCode(ColorCode.YELLOW.getCode());
                }
                scheduleMapper.insert(course);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                errors.add(String.format("课程 %s 导入失败：%s", course.getCourseName(), e.getMessage()));
            }
        }
        
        logEntry.setSuccessRecords(successCount);
        logEntry.setFailedRecords(failedCount);
        logEntry.setErrorDetails(errors.isEmpty() ? null : String.join("\n", errors));
        importLogMapper.insert(logEntry);
        
        log.info("用户 {} 导入课程：成功 {} 条，失败 {} 条", userId, successCount, failedCount);
        return logEntry;
    }
    
    public List<CourseSchedule> exportCourses(Long userId) {
        return scheduleMapper.findByUserId(userId);
    }
    
    public boolean checkConflict(Long userId, LocalDateTime activityStart, LocalDateTime activityEnd) {
        List<CourseSchedule> courses = scheduleMapper.findByUserId(userId);
        
        DayOfWeek activityDayOfWeek = activityStart.getDayOfWeek();
        int dayOfWeekValue = activityDayOfWeek.getValue();
        
        LocalTime activityStartTime = activityStart.toLocalTime();
        LocalTime activityEndTime = activityEnd.toLocalTime();
        
        WeekConfig semester = getCurrentSemester();
        int currentWeek = semester != null ? semester.getCurrentWeek() : 1;
        
        for (CourseSchedule course : courses) {
            if (!isCourseActiveInWeek(course, currentWeek)) {
                continue;
            }
            
            if (course.getDayOfWeek() != dayOfWeekValue) {
                continue;
            }
            
            TimeSlotConfig timeSlot = timeSlotMapper.findByPeriod(course.getPeriod());
            if (timeSlot == null) continue;
            
            LocalTime courseStart = LocalTime.parse(timeSlot.getStartTime());
            LocalTime courseEnd = LocalTime.parse(timeSlot.getEndTime());
            
            if (hasTimeOverlap(activityStartTime, activityEndTime, courseStart, courseEnd)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasTimeOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }
    
    public List<Map<String, Object>> getConflictingCourses(Long userId, LocalDateTime activityStart, LocalDateTime activityEnd) {
        List<CourseSchedule> courses = scheduleMapper.findByUserId(userId);
        List<Map<String, Object>> conflicts = new ArrayList<>();
        
        DayOfWeek activityDayOfWeek = activityStart.getDayOfWeek();
        int dayOfWeekValue = activityDayOfWeek.getValue();
        
        LocalTime activityStartTime = activityStart.toLocalTime();
        LocalTime activityEndTime = activityEnd.toLocalTime();
        
        WeekConfig semester = getCurrentSemester();
        int currentWeek = semester != null ? semester.getCurrentWeek() : 1;
        
        for (CourseSchedule course : courses) {
            if (!isCourseActiveInWeek(course, currentWeek)) {
                continue;
            }
            
            if (course.getDayOfWeek() != dayOfWeekValue) {
                continue;
            }
            
            TimeSlotConfig timeSlot = timeSlotMapper.findByPeriod(course.getPeriod());
            if (timeSlot == null) continue;
            
            LocalTime courseStart = LocalTime.parse(timeSlot.getStartTime());
            LocalTime courseEnd = LocalTime.parse(timeSlot.getEndTime());
            
            if (hasTimeOverlap(activityStartTime, activityEndTime, courseStart, courseEnd)) {
                Map<String, Object> conflict = new HashMap<>();
                conflict.put("courseId", course.getId());
                conflict.put("courseName", course.getCourseName());
                conflict.put("dayOfWeek", course.getDayOfWeek());
                conflict.put("period", course.getPeriod());
                conflict.put("timeSlot", timeSlot.getStartTime() + "-" + timeSlot.getEndTime());
                conflict.put("classroom", course.getClassroom());
                conflicts.add(conflict);
            }
        }
        
        return conflicts;
    }
    
    @Transactional
    public ActivityConflictCheck recordConflictCheck(Long userId, Long activityId, 
                                                     LocalDateTime activityStart, LocalDateTime activityEnd) {
        List<Map<String, Object>> conflicts = getConflictingCourses(userId, activityStart, activityEnd);
        
        ActivityConflictCheck check = new ActivityConflictCheck();
        check.setUserId(userId);
        check.setActivityId(activityId);
        check.setActivityStartTime(activityStart);
        check.setActivityEndTime(activityEnd);
        check.setHasConflict(conflicts.isEmpty() ? 0 : 1);
        check.setCheckTime(LocalDateTime.now());
        
        if (!conflicts.isEmpty()) {
            List<Long> courseIds = conflicts.stream()
                    .map(c -> (Long) c.get("courseId"))
                    .collect(Collectors.toList());
            check.setConflictCourseIds(String.join(",", courseIds.stream().map(String::valueOf).collect(Collectors.toList())));
            check.setConflictDetails(conflicts.toString());
        }
        
        conflictMapper.insert(check);
        return check;
    }
    
    public List<Long> filterActivitiesWithoutConflict(Long userId, List<Long> activityIds, 
                                                       Map<Long, LocalDateTime[]> activityTimes) {
        List<Long> filtered = new ArrayList<>();
        
        for (Long activityId : activityIds) {
            LocalDateTime[] times = activityTimes.get(activityId);
            if (times != null && !checkConflict(userId, times[0], times[1])) {
                filtered.add(activityId);
            }
        }
        
        return filtered;
    }
    
    public Map<String, Object> getScheduleStatistics(Long userId) {
        List<CourseSchedule> courses = scheduleMapper.findByUserId(userId);
        
        int totalCourses = courses.size();
        int everyWeekCourses = (int) courses.stream().filter(c -> WeekType.EVERY.getCode().equals(c.getWeekType())).count();
        int singleWeekCourses = (int) courses.stream().filter(c -> WeekType.SINGLE.getCode().equals(c.getWeekType())).count();
        int doubleWeekCourses = (int) courses.stream().filter(c -> WeekType.DOUBLE.getCode().equals(c.getWeekType())).count();
        
        Map<Integer, Integer> dayDistribution = new HashMap<>();
        for (int day = 1; day <= 7; day++) {
            final int currentDay = day;
            dayDistribution.put(day, (int) courses.stream().filter(c -> c.getDayOfWeek() == currentDay).count());
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCourses", totalCourses);
        stats.put("everyWeekCourses", everyWeekCourses);
        stats.put("singleWeekCourses", singleWeekCourses);
        stats.put("doubleWeekCourses", doubleWeekCourses);
        stats.put("dayDistribution", dayDistribution);
        
        return stats;
    }
}
