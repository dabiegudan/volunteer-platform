package com.volunteer.ai.service;

import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.mapper.ActivityMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIRecommendationService {

    private final ActivityMapper activityMapper;
    private final ChatModel chatModel;

    public List<Activity> getRecommendedActivities(Long userId, int limit) {
        List<Activity> allActivities = activityMapper.selectList(null);

        if (allActivities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Activity> activeActivities = allActivities.stream()
                .filter(activity -> Set.of("RECRUITING", "ONGOING", "PUBLISHED").contains(activity.getStatus()))
                .filter(activity -> activity.getStartTime() != null && activity.getStartTime().isAfter(LocalDateTime.now()))
                .toList();

        if (activeActivities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Activity> recommended = new ArrayList<>(activeActivities);
        recommended.sort((a, b) -> {
            int aScore = calculateScore(a, userId);
            int bScore = calculateScore(b, userId);
            return Integer.compare(bScore, aScore);
        });

        return recommended.subList(0, Math.min(limit, recommended.size()));
    }

    public Map<String, Object> getAIRecommendations(Long userId, int limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Activity> recommended = getRecommendedActivities(userId, limit);

            if (recommended.isEmpty()) {
                result.put("recommendations", Collections.emptyList());
                result.put("reasoning", "暂无合适的活动推荐");
                return result;
            }

            Map<String, Integer> userInterests = getUserInterests(userId);

            StringBuilder activitiesInfo = new StringBuilder();
            for (Activity activity : recommended) {
                activitiesInfo.append(String.format(
                        "活动ID: %d, 名称: %s, 分类: %s, 时间: %s, 地点: %s, 描述: %s\n",
                        activity.getId(),
                        activity.getTitle(),
                        activity.getCategory(),
                        activity.getStartTime(),
                        activity.getLocation(),
                        activity.getDescription() != null ? activity.getDescription().substring(0, Math.min(100, activity.getDescription().length())) + "..." : "无"
                ));
            }

            String prompt = String.format("""
                    你是一个志愿者活动推荐助手。请根据以下信息为用户生成个性化活动推荐：

                    用户兴趣标签: %s

                    可用活动列表:
                    %s

                    请为用户推荐最合适的%d个活动，并提供详细的推荐理由。
                    推荐理由需要说明为什么推荐这些活动，以及它们与用户兴趣的匹配度。
                    请用自然、流畅的中文回答。
                    """, userInterests, activitiesInfo, Math.min(limit, recommended.size()));

            String reasoning = chatModel.chat(prompt);

            result.put("recommendations", recommended);
            result.put("reasoning", reasoning);
            result.put("userInterests", userInterests);

            log.debug("AI推荐完成，推荐活动数: {}", recommended.size());

        } catch (Exception e) {
            log.error("AI推荐失败，使用默认推荐", e);
            result.put("recommendations", getRecommendedActivities(userId, limit));
            result.put("reasoning", "推荐系统暂时使用默认规则，稍后将提供更智能的推荐");
        }

        return result;
    }

    public Map<String, Object> getRecommendationsWithExplanation(Long userId, int limit) {
        Map<String, Object> result = new HashMap<>();
        List<Activity> recommended = getRecommendedActivities(userId, limit);

        List<Map<String, Object>> detailedRecommendations = new ArrayList<>();
        for (Activity activity : recommended) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("activity", activity);
            detail.put("matchScore", calculateScore(activity, userId));
            detail.put("matchReasons", getMatchReasons(activity, userId));
            detailedRecommendations.add(detail);
        }

        result.put("recommendations", detailedRecommendations);
        return result;
    }

    private int calculateScore(Activity activity, Long userId) {
        int score = 0;

        score += calculateCategoryScore(activity, userId);
        score += calculateTimeScore(activity);
        score += calculatePopularityScore(activity);
        score += calculateFreshnessScore(activity);

        return score;
    }

    private int calculateCategoryScore(Activity activity, Long userId) {
        Map<String, Integer> userInterests = getUserInterests(userId);
        String category = activity.getCategory();
        return userInterests.getOrDefault(category, 3);
    }

    private int calculateTimeScore(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityTime = activity.getStartTime();

        if (activityTime == null) {
            return 0;
        }

        long hoursDiff = Duration.between(now, activityTime).toHours();

        if (hoursDiff < 0) {
            return 0;
        } else if (hoursDiff <= 24) {
            return 10;
        } else if (hoursDiff <= 72) {
            return 8;
        } else if (hoursDiff <= 168) {
            return 5;
        } else if (hoursDiff <= 336) {
            return 3;
        } else {
            return 1;
        }
    }

    private int calculatePopularityScore(Activity activity) {
        Integer maxMembers = activity.getMaxParticipants();
        Integer registeredCount = activity.getCurrentParticipants();

        if (maxMembers == null || registeredCount == null || maxMembers == 0) {
            return 0;
        }

        double ratio = (double) registeredCount / maxMembers;

        if (ratio >= 0.8) {
            return 8;
        } else if (ratio >= 0.5) {
            return 5;
        } else if (ratio >= 0.3) {
            return 3;
        } else {
            return 1;
        }
    }

    private int calculateFreshnessScore(Activity activity) {
        LocalDateTime createTime = activity.getCreateTime();
        if (createTime == null) {
            return 0;
        }

        long hoursDiff = Duration.between(createTime, LocalDateTime.now()).toHours();

        if (hoursDiff <= 24) {
            return 5;
        } else if (hoursDiff <= 72) {
            return 3;
        } else if (hoursDiff <= 168) {
            return 2;
        } else {
            return 0;
        }
    }

    private List<String> getMatchReasons(Activity activity, Long userId) {
        List<String> reasons = new ArrayList<>();
        Map<String, Integer> interests = getUserInterests(userId);

        if (interests.containsKey(activity.getCategory())) {
            reasons.add("活动分类「" + activity.getCategory() + "」与您的兴趣匹配");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityTime = activity.getStartTime();
        if (activityTime != null) {
            long hoursDiff = Duration.between(now, activityTime).toHours();
            if (hoursDiff <= 72) {
                reasons.add("活动即将开始（3天内）");
            }
        }

        Integer maxMembers = activity.getMaxParticipants();
        Integer registeredCount = activity.getCurrentParticipants();
        if (maxMembers != null && registeredCount != null) {
            double ratio = (double) registeredCount / maxMembers;
            if (ratio >= 0.5) {
                reasons.add("活动人气较高，已有" + registeredCount + "/" + maxMembers + "人报名");
            }
        }

        return reasons;
    }

    private Map<String, Integer> getUserInterests(Long userId) {
        Map<String, Integer> interests = new HashMap<>();
        interests.put("环保", 10);
        interests.put("教育", 8);
        interests.put("社区服务", 6);
        interests.put("文化艺术", 5);
        interests.put("体育", 4);
        interests.put("公益", 10);
        interests.put("敬老", 7);
        interests.put("助学", 8);
        interests.put("医疗", 6);
        interests.put("科技", 5);
        return interests;
    }
}
