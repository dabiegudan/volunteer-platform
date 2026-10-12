package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.report.entity.*;
import com.volunteer.report.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserProfileMapper profileMapper;
    private final ActivityPreferenceMapper preferenceMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationLogMapper logMapper;
    private final PopularActivityMapper popularMapper;
    private final ActivityMapper activityMapper;

    private static final int MAX_RECOMMENDATIONS = 10;

    public UserProfile getOrCreateUserProfile(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setAvailabilityScore(50);
            profile.setReliabilityScore(50);
            profileMapper.insert(profile);
        }
        return profile;
    }

    @Transactional
    public void updateUserProfile(Long userId, Map<String, Object> updates) {
        UserProfile profile = getOrCreateUserProfile(userId);

        if (updates.containsKey("interests")) {
            profile.setInterests((String) updates.get("interests"));
        }
        if (updates.containsKey("preferredAreas")) {
            profile.setPreferredAreas((String) updates.get("preferredAreas"));
        }
        if (updates.containsKey("preferredTimeSlots")) {
            profile.setPreferredTimeSlots((String) updates.get("preferredTimeSlots"));
        }
        if (updates.containsKey("skillTags")) {
            profile.setSkillTags((String) updates.get("skillTags"));
        }
        if (updates.containsKey("personalityType")) {
            profile.setPersonalityType((String) updates.get("personalityType"));
        }

        profileMapper.updateById(profile);
    }

    @Transactional
    public void addActivityPreference(Long userId, String activityType, String category, Integer priority) {
        ActivityPreference existing = preferenceMapper.findByUserIdAndType(userId, activityType);
        if (existing != null) {
            existing.setPriority(priority);
            preferenceMapper.updateById(existing);
        } else {
            ActivityPreference preference = new ActivityPreference();
            preference.setUserId(userId);
            preference.setActivityType(activityType);
            preference.setCategory(category);
            preference.setPriority(priority != null ? priority : 3);
            preferenceMapper.insert(preference);
        }
    }

    @Transactional
    public void removeActivityPreference(Long userId, String activityType) {
        ActivityPreference preference = preferenceMapper.findByUserIdAndType(userId, activityType);
        if (preference != null) {
            preferenceMapper.deleteById(preference.getId());
        }
    }

    public List<ActivityPreference> getUserPreferences(Long userId) {
        return preferenceMapper.findByUserId(userId);
    }

    public Map<String, Object> getUserProfileInfo(Long userId) {
        UserProfile profile = getOrCreateUserProfile(userId);
        List<ActivityPreference> preferences = preferenceMapper.findByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("interests", profile.getInterests());
        result.put("preferredAreas", profile.getPreferredAreas());
        result.put("preferredTimeSlots", profile.getPreferredTimeSlots());
        result.put("skillTags", profile.getSkillTags());
        result.put("personalityType", profile.getPersonalityType());
        result.put("availabilityScore", profile.getAvailabilityScore());
        result.put("reliabilityScore", profile.getReliabilityScore());
        result.put("preferences", preferences.stream().map(p -> {
            Map<String, Object> pref = new HashMap<>();
            pref.put("activityType", p.getActivityType());
            pref.put("category", p.getCategory());
            pref.put("priority", p.getPriority());
            return pref;
        }).collect(Collectors.toList()));

        return result;
    }

    public List<Map<String, Object>> getRecommendations(Long userId, int limit) {
        recommendationMapper.deleteExpired(LocalDateTime.now());

        List<Recommendation> recommendations = recommendationMapper.findByUserIdOrderByScore(userId);
        if (recommendations.size() < limit) {
            generateRecommendations(userId);
            recommendations = recommendationMapper.findByUserIdOrderByScore(userId);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(recommendations.size(), limit); i++) {
            Recommendation rec = recommendations.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("activityId", rec.getActivityId());
            item.put("activityName", loadActivityTitle(rec.getActivityId()));
            item.put("score", rec.getScore());
            item.put("reason", rec.getReason());
            item.put("source", rec.getSource());
            result.add(item);
        }

        return result;
    }

    @Transactional
    public void generateRecommendations(Long userId) {
        UserProfile profile = getOrCreateUserProfile(userId);
        List<ActivityPreference> preferences = preferenceMapper.findByUserId(userId);

        Set<String> preferredTypes = preferences.stream()
                .map(ActivityPreference::getActivityType)
                .collect(Collectors.toSet());

        List<Recommendation> existingRecs = recommendationMapper.findByUserId(userId);
        Set<Long> existingActivityIds = existingRecs.stream()
                .map(Recommendation::getActivityId)
                .collect(Collectors.toSet());

        List<Activity> candidateActivities = generateCandidateActivities(userId, preferredTypes);

        List<Recommendation> newRecommendations = new ArrayList<>();
        for (Activity activity : candidateActivities) {
            Long activityId = activity.getId();
            if (existingActivityIds.contains(activityId)) continue;

            double score = calculateScore(userId, activity, profile, preferences);
            String reason = generateReason(activity, profile, preferences);

            Recommendation rec = new Recommendation();
            rec.setUserId(userId);
            rec.setActivityId(activityId);
            rec.setScore(score);
            rec.setReason(reason);
            rec.setSource("SYSTEM");
            rec.setExpiresAt(LocalDateTime.now().plusDays(7));
            newRecommendations.add(rec);

            if (newRecommendations.size() >= MAX_RECOMMENDATIONS) break;
        }

        for (Recommendation rec : newRecommendations) {
            recommendationMapper.insert(rec);
        }
    }

    private List<Activity> generateCandidateActivities(Long userId, Set<String> preferredTypes) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Activity::getStatus,
                        ActivityStatusEnum.RECRUITING.name(),
                        ActivityStatusEnum.PUBLISHED.name(),
                        ActivityStatusEnum.ONGOING.name())
                .and(w -> w.isNull(Activity::getMaxParticipants)
                        .or()
                        .apply("current_participants < max_participants"))
                .orderByAsc(Activity::getStartTime)
                .orderByDesc(Activity::getCreateTime)
                .last("LIMIT 50");

        List<Activity> activities = activityMapper.selectList(wrapper);
        activities.sort(Comparator
                .comparingInt((Activity activity) -> preferredActivityRank(preferredTypes, activity))
                .thenComparingLong(activity -> stableRank(userId, activity.getId())));
        return activities;
    }

    private double calculateScore(Long userId, Activity activity, UserProfile profile, List<ActivityPreference> preferences) {
        double score = 50.0;
        String activityText = searchableActivityText(activity);

        for (ActivityPreference pref : preferences) {
            if (matchesActivityPreference(activityText, pref)) {
                score += safeScore(pref.getPriority(), 3) * 10;
            }
        }

        if (matchesText(activityText, profile.getInterests())) {
            score += 12;
        }
        if (matchesText(activityText, profile.getSkillTags())) {
            score += 10;
        }
        if (matchesText(safe(activity.getLocation()), profile.getPreferredAreas())) {
            score += 8;
        }

        score += safeScore(profile.getAvailabilityScore(), 50) * 0.2;
        score += safeScore(profile.getReliabilityScore(), 50) * 0.1;
        score += stableAffinityBoost(userId, activity.getId());

        return Math.min(100.0, Math.max(0.0, score));
    }

    private int safeScore(Integer score, int fallback) {
        return score != null ? score : fallback;
    }

    private long stableRank(Long userId, Long activityId) {
        long userPart = userId != null ? userId : 0L;
        long activityPart = activityId != null ? activityId : 0L;
        return Math.abs(userPart * 31 + activityPart * 17);
    }

    private double stableAffinityBoost(Long userId, Long activityId) {
        return (stableRank(userId, activityId) % 100) / 10.0;
    }

    private String generateReason(Activity activity, UserProfile profile, List<ActivityPreference> preferences) {
        String activityName = activity.getTitle() != null ? activity.getTitle() : "活动";
        List<String> reasons = new ArrayList<>();
        String activityText = searchableActivityText(activity);

        for (ActivityPreference pref : preferences) {
            if (pref.getPriority() >= 4 && matchesActivityPreference(activityText, pref)) {
                reasons.add("您关注" + pref.getActivityType());
                break;
            }
        }

        if (matchesText(activityText, profile.getSkillTags())) {
            reasons.add("您的技能与活动匹配");
        }

        if (reasons.isEmpty()) {
            reasons.add("根据您的活跃度推荐");
        }

        return String.join("，", reasons) + "，推荐您参加「" + activityName + "」";
    }

    private int preferredActivityRank(Set<String> preferredTypes, Activity activity) {
        if (preferredTypes == null || preferredTypes.isEmpty()) {
            return 1;
        }
        String activityText = searchableActivityText(activity);
        return preferredTypes.stream().anyMatch(type -> containsIgnoreCase(activityText, type)) ? 0 : 1;
    }

    private boolean matchesActivityPreference(String activityText, ActivityPreference pref) {
        return containsIgnoreCase(activityText, pref.getActivityType())
                || containsIgnoreCase(activityText, pref.getCategory());
    }

    private boolean matchesText(String text, String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return false;
        }
        return Arrays.stream(keywords.split("[,，;；\\s]+"))
                .filter(token -> !token.isBlank())
                .anyMatch(token -> containsIgnoreCase(text, token));
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private String searchableActivityText(Activity activity) {
        if (activity == null) {
            return "";
        }
        return String.join(" ",
                safe(activity.getTitle()),
                safe(activity.getDescription()),
                safe(activity.getCategory()),
                safe(activity.getTags()),
                safe(activity.getLocation()),
                safe(activity.getLeaderName()),
                safe(activity.getLeaderOrgUnit())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String loadActivityTitle(Long activityId) {
        if (activityId == null) {
            return "未知活动";
        }
        Activity activity = activityMapper.selectById(activityId);
        return activity != null && activity.getTitle() != null ? activity.getTitle() : "未知活动";
    }

    private Map<Long, String> loadActivityTitles(Set<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityMapper.selectBatchIds(activityIds).stream()
                .filter(activity -> activity.getId() != null)
                .collect(Collectors.toMap(
                        Activity::getId,
                        activity -> activity.getTitle() != null ? activity.getTitle() : "未知活动",
                        (left, right) -> left
                ));
    }

    @Transactional
    public void logRecommendationAction(Long userId, Long activityId, String action, String sessionId) {
        RecommendationLog log = new RecommendationLog();
        log.setUserId(userId);
        log.setActivityId(activityId);
        log.setAction(action);
        log.setActionTime(LocalDateTime.now());
        log.setSessionId(sessionId);
        logMapper.insert(log);
    }

    @Transactional
    public void recordActivityView(Long activityId) {
        LocalDate today = LocalDate.now();
        PopularActivity pop = popularMapper.findByActivityIdAndDate(activityId, today);

        if (pop != null) {
            pop.setViewCount(pop.getViewCount() + 1);
            updateHotScore(pop);
            popularMapper.updateById(pop);
        } else {
            pop = new PopularActivity();
            pop.setActivityId(activityId);
            pop.setViewCount(1);
            pop.setApplyCount(0);
            pop.setShareCount(0);
            pop.setHotScore(calculateHotScore(1, 0, 0));
            pop.setDate(today);
            popularMapper.insert(pop);
        }
    }

    @Transactional
    public void recordActivityApply(Long activityId) {
        LocalDate today = LocalDate.now();
        PopularActivity pop = popularMapper.findByActivityIdAndDate(activityId, today);

        if (pop != null) {
            pop.setApplyCount(pop.getApplyCount() + 1);
            updateHotScore(pop);
            popularMapper.updateById(pop);
        } else {
            pop = new PopularActivity();
            pop.setActivityId(activityId);
            pop.setViewCount(0);
            pop.setApplyCount(1);
            pop.setShareCount(0);
            pop.setHotScore(calculateHotScore(0, 1, 0));
            pop.setDate(today);
            popularMapper.insert(pop);
        }
    }

    @Transactional
    public void recordActivityShare(Long activityId) {
        LocalDate today = LocalDate.now();
        PopularActivity pop = popularMapper.findByActivityIdAndDate(activityId, today);

        if (pop != null) {
            pop.setShareCount(pop.getShareCount() + 1);
            updateHotScore(pop);
            popularMapper.updateById(pop);
        } else {
            pop = new PopularActivity();
            pop.setActivityId(activityId);
            pop.setViewCount(0);
            pop.setApplyCount(0);
            pop.setShareCount(1);
            pop.setHotScore(calculateHotScore(0, 0, 1));
            pop.setDate(today);
            popularMapper.insert(pop);
        }
    }

    private void updateHotScore(PopularActivity pop) {
        pop.setHotScore(calculateHotScore(pop.getViewCount(), pop.getApplyCount(), pop.getShareCount()));
    }

    private double calculateHotScore(int views, int applies, int shares) {
        return views * 1.0 + applies * 5.0 + shares * 10.0;
    }

    public List<Map<String, Object>> getHotActivities(int limit) {
        LocalDate today = LocalDate.now();
        List<PopularActivity> popularActivities = popularMapper.findTopHotActivities(today, limit);

        Map<Long, String> activityNames = loadActivityTitles(
                popularActivities.stream()
                        .map(PopularActivity::getActivityId)
                        .collect(Collectors.toSet())
        );

        return popularActivities.stream().map(pop -> {
            Map<String, Object> item = new HashMap<>();
            item.put("activityId", pop.getActivityId());
            item.put("activityName", activityNames.getOrDefault(pop.getActivityId(), "未知活动"));
            item.put("viewCount", pop.getViewCount());
            item.put("applyCount", pop.getApplyCount());
            item.put("shareCount", pop.getShareCount());
            item.put("hotScore", pop.getHotScore());
            return item;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void updateAvailabilityScore(Long userId, int increment) {
        UserProfile profile = getOrCreateUserProfile(userId);
        int newScore = Math.min(100, Math.max(0, profile.getAvailabilityScore() + increment));
        profile.setAvailabilityScore(newScore);
        profileMapper.updateById(profile);
    }

    @Transactional
    public void updateReliabilityScore(Long userId, int increment) {
        UserProfile profile = getOrCreateUserProfile(userId);
        int newScore = Math.min(100, Math.max(0, profile.getReliabilityScore() + increment));
        profile.setReliabilityScore(newScore);
        profileMapper.updateById(profile);
    }
}
