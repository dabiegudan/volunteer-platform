package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.report.entity.*;
import com.volunteer.report.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    
    private final ScheduleService scheduleService;
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/time-slots")
    public ResponseEntity<List<Map<String, Object>>> getTimeSlots() {
        List<TimeSlotConfig> slots = scheduleService.getTimeSlots();
        List<Map<String, Object>> result = slots.stream().map(slot -> {
            Map<String, Object> item = new HashMap<>();
            item.put("period", slot.getPeriod());
            item.put("startTime", slot.getStartTime());
            item.put("endTime", slot.getEndTime());
            item.put("duration", slot.getDurationMinutes());
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/semester")
    public ResponseEntity<Map<String, Object>> getCurrentSemester() {
        WeekConfig semester = scheduleService.getCurrentSemester();
        if (semester == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("semesterId", semester.getSemesterId());
        result.put("totalWeeks", semester.getTotalWeeks());
        result.put("currentWeek", semester.getCurrentWeek());
        result.put("startDate", semester.getSemesterStartDate());
        result.put("endDate", semester.getSemesterEndDate());
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getUserSchedule(@RequestAttribute("userId") Long userId) {
        List<CourseSchedule> courses = scheduleService.getUserSchedule(userId);
        List<Map<String, Object>> result = courses.stream().map(course -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("courseName", course.getCourseName());
            item.put("courseCode", course.getCourseCode());
            item.put("teacherName", course.getTeacherName());
            item.put("classroom", course.getClassroom());
            item.put("dayOfWeek", course.getDayOfWeek());
            item.put("period", course.getPeriod());
            item.put("startWeek", course.getStartWeek());
            item.put("endWeek", course.getEndWeek());
            item.put("weekType", course.getWeekType());
            item.put("colorCode", course.getColorCode());
            item.put("notes", course.getNotes());
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/grid")
    public ResponseEntity<Map<String, Object>> getScheduleGrid(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer week) {
        Map<String, Object> grid = scheduleService.getScheduleGrid(userId, week);
        return ResponseEntity.ok(grid);
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addCourse(
            @RequestAttribute("userId") Long userId,
            @RequestBody CourseSchedule course) {
        try {
            CourseSchedule added = scheduleService.addCourse(userId, course);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("courseId", added.getId());
            result.put("message", "课程添加成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @RequestAttribute("userId") Long userId,
            @RequestParam Long courseId,
            @RequestBody CourseSchedule updates) {
        try {
            CourseSchedule updated = scheduleService.updateCourse(userId, courseId, updates);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("course", updated);
            result.put("message", "课程更新成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteCourse(@RequestAttribute("userId") Long userId, @RequestParam Long courseId) {
        scheduleService.deleteCourse(userId, courseId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "课程删除成功");
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAllCourses(@RequestAttribute("userId") Long userId) {
        scheduleService.deleteAllCourses(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "所有课程已清空");
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importCourses(
            @RequestAttribute("userId") Long userId,
            @RequestParam String importType,
            @RequestParam(required = false) String fileName,
            @RequestBody List<CourseSchedule> courses) {
        CourseImportLog log = scheduleService.importCourses(userId, courses, importType, fileName);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalRecords", log.getTotalRecords());
        result.put("successRecords", log.getSuccessRecords());
        result.put("failedRecords", log.getFailedRecords());
        result.put("message", String.format("导入完成：成功%d条，失败%d条", 
                log.getSuccessRecords(), log.getFailedRecords()));
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/export")
    public ResponseEntity<List<Map<String, Object>>> exportCourses(@RequestAttribute("userId") Long userId) {
        List<CourseSchedule> courses = scheduleService.exportCourses(userId);
        List<Map<String, Object>> result = courses.stream().map(course -> {
            Map<String, Object> item = new HashMap<>();
            item.put("courseName", course.getCourseName());
            item.put("courseCode", course.getCourseCode());
            item.put("teacherName", course.getTeacherName());
            item.put("classroom", course.getClassroom());
            item.put("dayOfWeek", course.getDayOfWeek());
            item.put("period", course.getPeriod());
            item.put("startWeek", course.getStartWeek());
            item.put("endWeek", course.getEndWeek());
            item.put("weekType", course.getWeekType());
            item.put("notes", course.getNotes());
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/conflict/check")
    public ResponseEntity<Map<String, Object>> checkConflict(
            @RequestAttribute("userId") Long userId,
            @RequestParam String activityStartTime,
            @RequestParam String activityEndTime) {
        LocalDateTime start = LocalDateTime.parse(activityStartTime);
        LocalDateTime end = LocalDateTime.parse(activityEndTime);
        
        boolean hasConflict = scheduleService.checkConflict(userId, start, end);
        List<Map<String, Object>> conflicts = scheduleService.getConflictingCourses(userId, start, end);
        
        Map<String, Object> result = new HashMap<>();
        result.put("hasConflict", hasConflict);
        result.put("conflictCount", conflicts.size());
        result.put("conflicts", conflicts);
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @PostMapping("/conflict/filter")
    public ResponseEntity<Map<String, Object>> filterActivities(
            @RequestAttribute("userId") Long userId,
            @RequestBody Map<Long, String[]> activityTimes) {
        Map<Long, LocalDateTime[]> times = new HashMap<>();
        for (Map.Entry<Long, String[]> entry : activityTimes.entrySet()) {
            LocalDateTime[] arr = new LocalDateTime[2];
            arr[0] = LocalDateTime.parse(entry.getValue()[0]);
            arr[1] = LocalDateTime.parse(entry.getValue()[1]);
            times.put(entry.getKey(), arr);
        }
        
        List<Long> filtered = scheduleService.filterActivitiesWithoutConflict(userId, 
                new ArrayList<>(activityTimes.keySet()), times);
        
        Map<String, Object> result = new HashMap<>();
        result.put("filteredActivities", filtered);
        result.put("totalActivities", activityTimes.size());
        result.put("availableActivities", filtered.size());
        return ResponseEntity.ok(result);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(@RequestAttribute("userId") Long userId) {
        Map<String, Object> stats = scheduleService.getScheduleStatistics(userId);
        return ResponseEntity.ok(stats);
    }
    
    @RequiresAccess(requireStudentId = true)
    @GetMapping("/week-courses")
    public ResponseEntity<List<Map<String, Object>>> getUserScheduleByWeek(
            @RequestAttribute("userId") Long userId,
            @RequestParam Integer week) {
        List<CourseSchedule> courses = scheduleService.getUserScheduleByWeek(userId, week);
        List<Map<String, Object>> result = courses.stream().map(course -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("courseName", course.getCourseName());
            item.put("dayOfWeek", course.getDayOfWeek());
            item.put("period", course.getPeriod());
            item.put("weekType", course.getWeekType());
            item.put("classroom", course.getClassroom());
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
