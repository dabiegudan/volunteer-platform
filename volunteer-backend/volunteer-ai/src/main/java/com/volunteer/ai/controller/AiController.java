package com.volunteer.ai.controller;

import com.volunteer.activity.entity.Activity;
import com.volunteer.ai.service.*;
import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "AI服务", description = "智能推荐、情感分析、内容处理、知识库、排班管理接口")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AIRecommendationService aiRecommendationService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final ContentService contentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SummaryGeneratorService summaryGeneratorService;
    private final SchedulingService schedulingService;
    private final ActivityDraftGeneratorService activityDraftGeneratorService;

    @Operation(summary = "获取活动推荐")
    @GetMapping("/recommend")
    public Result<List<Activity>> getRecommendations(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "推荐数量") @RequestParam(defaultValue = "10") int limit) {
        log.debug("获取推荐活动, userId: {}, limit: {}", userId, limit);
        List<Activity> recommendations = aiRecommendationService.getRecommendedActivities(userId, limit);
        return Result.success(recommendations);
    }

    @Operation(summary = "获取AI智能推荐（带推荐理由）")
    @GetMapping("/recommend/ai")
    public Result<Map<String, Object>> getAIRecommendations(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "推荐数量") @RequestParam(defaultValue = "10") int limit) {
        log.debug("获取AI推荐, userId: {}, limit: {}", userId, limit);
        Map<String, Object> recommendations = aiRecommendationService.getAIRecommendations(userId, limit);
        return Result.success(recommendations);
    }

    @Operation(summary = "获取推荐详情（带匹配度分析）")
    @GetMapping("/recommend/detail")
    public Result<Map<String, Object>> getRecommendationsWithExplanation(
            @Parameter(description = "用户ID") @RequestAttribute("userId") Long userId,
            @Parameter(description = "推荐数量") @RequestParam(defaultValue = "10") int limit) {
        log.debug("获取推荐详情, userId: {}, limit: {}", userId, limit);
        Map<String, Object> recommendations = aiRecommendationService.getRecommendationsWithExplanation(userId, limit);
        return Result.success(recommendations);
    }

    @Operation(summary = "根据描述生成活动草稿")
    @PostMapping("/activity/draft")
    public Result<Map<String, Object>> generateActivityDraft(@RequestBody Map<String, Object> request) {
        String description = firstText(request, "description", "prompt", "text");
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("活动描述不能为空");
        }
        log.debug("生成活动草稿, 描述长度: {}", description.length());
        return Result.success(activityDraftGeneratorService.generateDraft(description, readCurrentDraft(request)));
    }

    @Operation(summary = "情感分析")
    @PostMapping("/sentiment")
    public Result<Map<String, Object>> analyzeSentiment(@Parameter(description = "待分析文本") @RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }
        log.debug("情感分析, 文本长度: {}", text.length());
        SentimentAnalysisService.SentimentResult result = sentimentAnalysisService.analyze(text);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("sentiment", result.getSentiment());
        response.put("score", result.getScore());
        response.put("analysis", result.getAnalysis());
        response.put("positiveWords", result.getPositiveWords());
        response.put("negativeWords", result.getNegativeWords());

        return Result.success(response);
    }

    @Operation(summary = "申请审核分析")
    @PostMapping("/application/analyze")
    public Result<Map<String, Object>> analyzeApplication(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String activityCategory = request.get("activityCategory");

        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("申请理由不能为空");
        }
        log.debug("申请审核分析, 文本长度: {}, 活动分类: {}", text.length(), activityCategory);
        Map<String, Object> result = sentimentAnalysisService.analyzeApplication(text, activityCategory);
        return Result.success(result);
    }

    @Operation(summary = "批量申请审核分析")
    @PostMapping("/application/batch-analyze")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> batchAnalyzeApplications(@RequestBody List<Map<String, String>> applications) {
        if (applications == null || applications.isEmpty()) {
            throw new IllegalArgumentException("申请列表不能为空");
        }
        log.debug("批量申请审核分析, 申请数量: {}", applications.size());
        Map<String, Object> result = sentimentAnalysisService.batchAnalyzeApplications(applications);
        return Result.success(result);
    }

    @Operation(summary = "生成活动总结")
    @PostMapping("/summary/generate")
    public Result<Map<String, Object>> generateSummary(@RequestBody Map<String, Object> request) {
        String activityName = (String) request.get("activityName");
        String keywords = (String) request.get("keywords");
        String activityType = (String) request.get("activityType");
        Integer serviceHours = request.get("serviceHours") != null ? ((Number) request.get("serviceHours")).intValue() : null;

        if (activityName == null || activityName.isEmpty()) {
            throw new IllegalArgumentException("活动名称不能为空");
        }
        log.debug("生成活动总结, 活动: {}", activityName);
        Map<String, Object> result = summaryGeneratorService.generateSummary(activityName, keywords, activityType, serviceHours);
        return Result.success(result);
    }

    @Operation(summary = "生成反思总结")
    @PostMapping("/summary/reflection")
    public Result<Map<String, Object>> generateReflection(@RequestBody Map<String, String> request) {
        String activityName = request.get("activityName");
        String personalExperience = request.get("personalExperience");

        if (activityName == null || activityName.isEmpty()) {
            throw new IllegalArgumentException("活动名称不能为空");
        }
        log.debug("生成反思总结, 活动: {}", activityName);
        Map<String, Object> result = summaryGeneratorService.generateReflection(activityName, personalExperience);
        return Result.success(result);
    }

    @Operation(summary = "生成证书内容")
    @PostMapping("/summary/certificate")
    public Result<Map<String, Object>> generateCertificateContent(@RequestBody Map<String, Object> request) {
        String activityName = (String) request.get("activityName");
        String volunteerName = (String) request.get("volunteerName");
        Integer serviceHours = request.get("serviceHours") != null ? ((Number) request.get("serviceHours")).intValue() : null;
        String date = (String) request.get("date");

        if (activityName == null || activityName.isEmpty()) {
            throw new IllegalArgumentException("活动名称不能为空");
        }
        if (volunteerName == null || volunteerName.isEmpty()) {
            throw new IllegalArgumentException("志愿者姓名不能为空");
        }
        log.debug("生成证书内容, 活动: {}, 志愿者: {}", activityName, volunteerName);
        Map<String, Object> result = summaryGeneratorService.generateCertificateContent(activityName, volunteerName, serviceHours, date);
        return Result.success(result);
    }

    @Operation(summary = "生成排班方案")
    @PostMapping("/schedule/generate")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> generateSchedule(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> volunteers = (List<Map<String, Object>>) request.get("volunteers");
        @SuppressWarnings("unchecked")
        Map<String, Object> activityInfo = (Map<String, Object>) request.get("activityInfo");

        if (volunteers == null || volunteers.isEmpty()) {
            throw new IllegalArgumentException("志愿者列表不能为空");
        }
        if (activityInfo == null || activityInfo.get("activityName") == null) {
            throw new IllegalArgumentException("活动信息不能为空");
        }
        log.debug("生成排班方案, 志愿者数: {}", volunteers.size());
        Map<String, Object> result = schedulingService.generateSchedule(volunteers, activityInfo);
        return Result.success(result);
    }

    @Operation(summary = "志愿者匹配")
    @PostMapping("/schedule/match")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> matchVolunteers(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> volunteers = (List<Map<String, Object>>) request.get("volunteers");
        @SuppressWarnings("unchecked")
        Map<String, Object> activityInfo = (Map<String, Object>) request.get("activityInfo");

        if (volunteers == null || volunteers.isEmpty()) {
            throw new IllegalArgumentException("志愿者列表不能为空");
        }
        if (activityInfo == null || activityInfo.get("activityName") == null) {
            throw new IllegalArgumentException("活动信息不能为空");
        }
        log.debug("志愿者匹配, 志愿者数: {}", volunteers.size());
        Map<String, Object> result = schedulingService.matchVolunteersToActivity(volunteers, activityInfo);
        return Result.success(result);
    }

    @Operation(summary = "生成多日排班方案")
    @PostMapping("/schedule/multi-day")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> generateMultiDaySchedule(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> volunteers = (List<Map<String, Object>>) request.get("volunteers");
        @SuppressWarnings("unchecked")
        Map<String, Object> activityInfo = (Map<String, Object>) request.get("activityInfo");
        Integer days = request.get("days") != null ? ((Number) request.get("days")).intValue() : 1;

        if (volunteers == null || volunteers.isEmpty()) {
            throw new IllegalArgumentException("志愿者列表不能为空");
        }
        if (activityInfo == null || activityInfo.get("activityName") == null) {
            throw new IllegalArgumentException("活动信息不能为空");
        }
        log.debug("生成多日排班方案, 志愿者数: {}, 天数: {}", volunteers.size(), days);
        Map<String, Object> result = schedulingService.generateMultiDaySchedule(volunteers, activityInfo, days);
        return Result.success(result);
    }

    @Operation(summary = "分析排班冲突")
    @PostMapping("/schedule/conflicts")
    public Result<Map<String, Object>> analyzeConflicts(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> volunteers = (List<Map<String, Object>>) request.get("volunteers");
        @SuppressWarnings("unchecked")
        Map<String, Object> activityInfo = (Map<String, Object>) request.get("activityInfo");

        if (volunteers == null || volunteers.isEmpty()) {
            throw new IllegalArgumentException("志愿者列表不能为空");
        }
        if (activityInfo == null) {
            throw new IllegalArgumentException("活动信息不能为空");
        }
        log.debug("分析排班冲突, 志愿者数: {}", volunteers.size());
        Map<String, Object> result = schedulingService.analyzeScheduleConflicts(volunteers, activityInfo);
        return Result.success(result);
    }

    @Operation(summary = "内容润色")
    @PostMapping("/polish")
    public Result<Map<String, Object>> polishContent(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        log.debug("内容润色, 长度: {}", content.length());
        String polished = contentService.polishContent(content);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("original", content);
        response.put("polished", polished);

        return Result.success(response);
    }

    @Operation(summary = "敏感词检测")
    @PostMapping("/sensitive")
    public Result<Map<String, Object>> detectSensitiveWords(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        log.debug("敏感词检测, 长度: {}", content.length());
        return Result.success(contentService.analyzeContent(content));
    }

    @Operation(summary = "内容审核处理")
    @PostMapping("/process")
    @RequiresAccess("organizer")
    public Result<Map<String, Object>> processContent(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        boolean autoPolish = request.get("autoPolish") != null && (Boolean) request.get("autoPolish");
        log.debug("内容审核处理, 长度: {}, 自动润色: {}", content.length(), autoPolish);
        return Result.success(contentService.processContent(content, autoPolish));
    }

    @Operation(summary = "添加知识到知识库")
    @PostMapping("/knowledge/add")
    @RequiresAccess("admin")
    public Result<Void> addKnowledge(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        String category = request.get("category");
        String source = request.get("source");

        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("知识内容不能为空");
        }
        log.debug("添加知识, 分类: {}", category);
        knowledgeBaseService.addText(text, category, source);
        return Result.success();
    }

    @Operation(summary = "搜索知识库")
    @GetMapping("/knowledge/search")
    public Result<List<Map<String, Object>>> searchKnowledge(
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "最大结果数") @RequestParam(defaultValue = "5") int maxResults,
            @Parameter(description = "最小相似度分数") @RequestParam(defaultValue = "0.0") double minScore) {
        log.debug("搜索知识库, 查询: {}, 最大结果: {}", query, maxResults);
        List<Map<String, Object>> results = knowledgeBaseService.search(query, maxResults, minScore);
        return Result.success(results);
    }

    @Operation(summary = "按分类搜索知识库")
    @GetMapping("/knowledge/search/category")
    public Result<List<Map<String, Object>>> searchKnowledgeByCategory(
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "分类") @RequestParam String category,
            @Parameter(description = "最大结果数") @RequestParam(defaultValue = "5") int maxResults) {
        log.debug("按分类搜索知识库, 查询: {}, 分类: {}", query, category);
        List<Map<String, Object>> results = knowledgeBaseService.searchByCategory(query, category, maxResults);
        return Result.success(results);
    }

    private String firstText(Map<String, Object> body, String... keys) {
        if (body == null) {
            return "";
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readCurrentDraft(Map<String, Object> body) {
        if (body == null || !(body.get("currentDraft") instanceof Map<?, ?> currentDraft)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) currentDraft;
    }
}
