package com.volunteer.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityDraftGeneratorService {

    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> CATEGORY_CODES = Set.of(
            "PUBLIC_WELFARE", "COMMUNITY", "ENVIRONMENT", "EDUCATION",
            "CULTURE", "EMERGENCY", "HEALTH", "OTHER", "CAMPUS", "ELDERLY", "MEDICAL"
    );
    private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
            Map.entry("公益服务", "PUBLIC_WELFARE"),
            Map.entry("社区服务", "COMMUNITY"),
            Map.entry("环保行动", "ENVIRONMENT"),
            Map.entry("环境保护", "ENVIRONMENT"),
            Map.entry("教育支持", "EDUCATION"),
            Map.entry("教育助学", "EDUCATION"),
            Map.entry("文化活动", "CULTURE"),
            Map.entry("文化传承", "CULTURE"),
            Map.entry("应急保障", "EMERGENCY"),
            Map.entry("应急救援", "EMERGENCY"),
            Map.entry("健康服务", "HEALTH"),
            Map.entry("健康医疗", "HEALTH"),
            Map.entry("校园服务", "CAMPUS"),
            Map.entry("助老服务", "ELDERLY"),
            Map.entry("医疗辅助", "MEDICAL"),
            Map.entry("其他服务", "OTHER"),
            Map.entry("其他", "OTHER")
    );
    private static final Pattern JSON_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateDraft(String description, Map<String, Object> currentDraft) {
        String normalizedDescription = normalizeDescription(description);
        Map<String, Object> fallback = buildFallbackDraft(normalizedDescription, currentDraft);

        try {
            String response = chatModel.chat(buildPrompt(normalizedDescription, currentDraft, fallback));
            Map<String, Object> aiDraft = parseJsonObject(response);
            Map<String, Object> normalizedDraft = normalizeDraft(aiDraft, fallback);
            normalizedDraft.put("aiGenerated", true);
            normalizedDraft.put("generationSource", "llm");
            return normalizedDraft;
        } catch (Exception e) {
            log.warn("AI活动草稿生成失败, 使用本地兜底: {}", e.getMessage());
            fallback.put("aiGenerated", false);
            fallback.put("generationSource", "fallback");
            return fallback;
        }
    }

    private String buildPrompt(String description, Map<String, Object> currentDraft, Map<String, Object> fallback) {
        String currentDraftJson = safeJson(currentDraft);
        String fallbackJson = safeJson(fallback);
        LocalDate today = LocalDate.now(PLATFORM_ZONE);
        return """
                你是一体式志愿者服务平台的活动策划助手。请根据用户提供的具体或模糊活动描述，生成一个可直接回填到“新建活动”表单的 JSON 对象。

                当前日期：%s，时区：Asia/Shanghai。
                用户描述：
                %s

                当前表单草稿：
                %s

                如果描述缺少时间、地点、人数或时长，请给出合理默认值；时间必须晚于当前日期，并使用 yyyy-MM-dd'T'HH:mm:ss。
                category 只能使用这些编码之一：PUBLIC_WELFARE, COMMUNITY, ENVIRONMENT, EDUCATION, CULTURE, EMERGENCY, HEALTH, OTHER, CAMPUS, ELDERLY, MEDICAL。
                同时生成一段可直接用于群通知/公告发布的招募通知文案，语气参考校内志愿招募通知，结构包含：开场、志愿保障、招募信息、招募要求、服务时间、服务地点、招募人数、服务内容、报名提醒、退出/加群/失信注意事项、咨询方式。未知的报名链接和联系方式可写成“待补充”。
                返回 JSON 必须只包含这些字段：
                title, location, category, startTime, endTime, durationHours, maxParticipants, organizerName, description, tags, notificationTitle, notificationContent。
                不要输出 Markdown、解释文字或代码块。

                参考兜底草稿：
                %s
                """.formatted(today, description, currentDraftJson, fallbackJson);
    }

    private Map<String, Object> parseJsonObject(String response) throws Exception {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("AI响应为空");
        }
        String json = response.trim();
        json = JSON_FENCE.matcher(json).replaceAll("").trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI响应不包含JSON对象");
        }
        return objectMapper.readValue(json.substring(start, end + 1), MAP_TYPE);
    }

    private Map<String, Object> normalizeDraft(Map<String, Object> draft, Map<String, Object> fallback) {
        Map<String, Object> result = new LinkedHashMap<>(fallback);

        putString(result, "title", firstText(draft, "title", "activityName", "name"), 255);
        putString(result, "location", firstText(draft, "location", "address"), 512);
        result.put("category", normalizeCategory(firstText(draft, "category", "activityCategory"), text(fallback.get("category"))));
        putString(result, "organizerName", firstText(draft, "organizerName", "leaderName", "organizer"), 80);
        putString(result, "description", firstText(draft, "description", "content", "summary"), 5000);

        double duration = normalizeDuration(draft.get("durationHours"), number(fallback.get("durationHours"), 3.0));
        LocalDateTime startTime = normalizeDateTime(draft.get("startTime"), text(fallback.get("startTime")));
        LocalDateTime endTime = normalizeDateTime(draft.get("endTime"), text(fallback.get("endTime")));
        if (!endTime.isAfter(startTime)) {
            endTime = startTime.plusMinutes(Math.round(duration * 60));
        } else {
            duration = normalizeDuration(durationBetween(startTime, endTime), duration);
        }

        result.put("startTime", formatDateTime(startTime));
        result.put("endTime", formatDateTime(endTime));
        result.put("durationHours", duration);
        result.put("maxParticipants", normalizeParticipants(draft.get("maxParticipants"), integer(fallback.get("maxParticipants"), 30)));
        result.put("tags", normalizeTags(draft.get("tags"), text(fallback.get("tags"))));
        String notificationTitle = firstText(draft, "notificationTitle", "noticeTitle", "announcementTitle");
        result.put("notificationTitle", StringUtils.hasText(notificationTitle)
                ? limit(notificationTitle.trim(), 255)
                : buildNotificationTitle(result));
        String notificationContent = firstText(draft, "notificationContent", "noticeContent", "announcementContent");
        result.put("notificationContent", StringUtils.hasText(notificationContent)
                ? limit(notificationContent.trim(), 8000)
                : buildNotificationContent(result));
        return result;
    }

    private Map<String, Object> buildFallbackDraft(String description, Map<String, Object> currentDraft) {
        double duration = inferDuration(description);
        LocalDateTime start = inferStartTime(description);
        LocalDateTime end = start.plusMinutes(Math.round(duration * 60));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("title", limit(readExisting(currentDraft, "title", inferTitle(description)), 255));
        draft.put("location", limit(readExisting(currentDraft, "location", inferLocation(description)), 512));
        draft.put("category", normalizeCategory(readExisting(currentDraft, "category", inferCategory(description)), "PUBLIC_WELFARE"));
        draft.put("startTime", readExisting(currentDraft, "startTime", formatDateTime(start)));
        draft.put("endTime", readExisting(currentDraft, "endTime", formatDateTime(end)));
        draft.put("durationHours", normalizeDuration(currentDraft == null ? null : currentDraft.get("durationHours"), duration));
        draft.put("maxParticipants", normalizeParticipants(currentDraft == null ? null : currentDraft.get("maxParticipants"), inferParticipants(description)));
        draft.put("organizerName", limit(readExisting(currentDraft, "organizerName", ""), 80));
        draft.put("description", limit(description, 5000));
        draft.put("tags", inferTags(description));
        draft.put("notificationTitle", limit(readExisting(currentDraft, "notificationTitle", buildNotificationTitle(draft)), 255));
        draft.put("notificationContent", limit(readExisting(currentDraft, "notificationContent", buildNotificationContent(draft)), 8000));
        return draft;
    }

    private String buildNotificationTitle(Map<String, Object> draft) {
        String title = text(draft.get("title"));
        return StringUtils.hasText(title) ? title + "招募通知" : "志愿服务活动招募通知";
    }

    private String buildNotificationContent(Map<String, Object> draft) {
        String title = withFallback(text(draft.get("title")), "志愿服务活动");
        String location = withFallback(text(draft.get("location")), "待补充");
        String organizerName = withFallback(text(draft.get("organizerName")), "活动负责人");
        String description = withFallback(text(draft.get("description")), title);
        String startTime = formatNoticeTime(text(draft.get("startTime")));
        String endTime = formatNoticeTime(text(draft.get("endTime")));
        String participants = withFallback(text(draft.get("maxParticipants")), "待补充");
        String duration = withFallback(text(draft.get("durationHours")), "待核定");

        return """
                小伙伴们大家好！
                %s开始招募志愿者啦！

                🔔志愿保障：
                1️⃣提供志愿服务时长；
                2️⃣提供二课积分；
                3️⃣提供志愿服务证明，具体以活动安排为准。

                🌻招募信息：
                🔔招募要求：
                1️⃣服从统一安排，具有志愿者精神，工作认真负责；
                2️⃣具有良好组织、沟通、协调能力；
                3️⃣能够按时参加培训、签到和现场服务；
                4️⃣如有特殊着装、课程或请假要求，请提前确认。

                🔔服务时间：
                %s 至 %s，预计累计服务 %s 小时。

                🔔服务地点：
                %s

                🔔招募人数：
                %s 人

                🔔服务内容：
                %s

                🌈报名链接：
                待补充

                ⚠️注意：
                💓一经参加，无重大或极特殊原因不允许退出，请确认个人时间后再报名。
                💓报名成功后请及时加入志愿者活动群，如未加群，请向相关负责人说明情况。
                ❗如因个人原因影响活动开展，将按平台规则记录志愿服务失信情况。
                🐣如对本次活动有任何疑问，请咨询：%s（联系方式待补充）。
                """.formatted(title, startTime, endTime, duration, location, participants, description, organizerName).trim();
    }

    private String formatNoticeTime(String value) {
        if (!StringUtils.hasText(value)) {
            return "待补充";
        }
        try {
            LocalDateTime dateTime = normalizeDateTime(value, value);
            return "%d月%d日 %02d:%02d".formatted(
                    dateTime.getMonthValue(),
                    dateTime.getDayOfMonth(),
                    dateTime.getHour(),
                    dateTime.getMinute()
            );
        } catch (Exception ignored) {
            return value;
        }
    }

    private String withFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("活动描述不能为空");
        }
        return limit(description.trim().replaceAll("\\s+", " "), 1200);
    }

    private String inferTitle(String description) {
        if (containsAny(description, "迎新", "新生")) return "校园迎新志愿服务";
        if (containsAny(description, "助老", "老人", "老年", "智能手机")) return "数字助老志愿服务";
        if (containsAny(description, "环保", "垃圾", "清洁", "分类")) return "社区环保清洁行动";
        if (containsAny(description, "运动会", "赛事")) return "校园赛事服务保障";
        if (containsAny(description, "图书馆", "书籍")) return "图书馆志愿服务";
        if (containsAny(description, "支教", "辅导", "课堂", "培训")) return "教育支持志愿服务";
        String cleaned = description.replaceAll("[，。；;,.!?！？\\s]+", "");
        if (cleaned.length() > 18) {
            cleaned = cleaned.substring(0, 18);
        }
        return StringUtils.hasText(cleaned) ? cleaned + "志愿服务" : "志愿服务活动";
    }

    private String inferLocation(String description) {
        if (containsAny(description, "图书馆")) return "重庆师范大学图书馆";
        if (containsAny(description, "社区", "助老", "老人", "老年")) return "大学城社区党群服务中心";
        if (containsAny(description, "运动会", "赛事", "操场", "田径场")) return "重庆师范大学田径场";
        if (containsAny(description, "迎新", "新生", "校园", "校内")) return "重庆师范大学大学城校区";
        return "重庆师范大学大学城校区";
    }

    private String inferCategory(String description) {
        if (containsAny(description, "校园", "迎新", "运动会", "赛事", "图书馆")) return "CAMPUS";
        if (containsAny(description, "环保", "垃圾", "清洁", "分类")) return "ENVIRONMENT";
        if (containsAny(description, "支教", "辅导", "课堂", "培训", "科普")) return "EDUCATION";
        if (containsAny(description, "社区", "便民")) return "COMMUNITY";
        if (containsAny(description, "应急", "救援", "安全")) return "EMERGENCY";
        if (containsAny(description, "健康", "医疗", "导诊")) return "HEALTH";
        if (containsAny(description, "文化", "展览", "讲解")) return "CULTURE";
        if (containsAny(description, "助老", "老人", "老年")) return "ELDERLY";
        return "PUBLIC_WELFARE";
    }

    private double inferDuration(String description) {
        if (containsAny(description, "全天")) return 8.0;
        if (containsAny(description, "半天")) return 4.0;
        if (containsAny(description, "课堂", "讲座", "培训")) return 2.0;
        if (containsAny(description, "运动会", "迎新")) return 6.0;
        return 3.0;
    }

    private int inferParticipants(String description) {
        if (containsAny(description, "大型", "运动会", "迎新")) return 60;
        if (containsAny(description, "课堂", "培训", "讲座", "助老")) return 20;
        return 30;
    }

    private LocalDateTime inferStartTime(String description) {
        LocalDate date = LocalDate.now(PLATFORM_ZONE).plusDays(7);
        LocalTime time = LocalTime.of(9, 0);
        if (containsAny(description, "晚上", "夜间")) {
            time = LocalTime.of(19, 0);
        } else if (containsAny(description, "下午")) {
            time = LocalTime.of(14, 0);
        } else if (containsAny(description, "中午")) {
            time = LocalTime.of(12, 30);
        }
        return LocalDateTime.of(date, time);
    }

    private String inferTags(String description) {
        List<String> tags = List.of("迎新", "助老", "环保", "社区", "校园", "支教", "培训", "健康", "文化", "应急");
        return tags.stream().filter(description::contains).limit(4).reduce((left, right) -> left + "," + right).orElse("志愿服务");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void putString(Map<String, Object> target, String key, String value, int maxLength) {
        if (StringUtils.hasText(value)) {
            target.put(key, limit(value.trim(), maxLength));
        }
    }

    private String firstText(Map<String, Object> source, String... keys) {
        if (source == null) return "";
        for (String key : keys) {
            String value = text(source.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String readExisting(Map<String, Object> source, String key, String fallback) {
        if (source == null) return fallback;
        String value = text(source.get(key));
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeCategory(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            String normalized = value.trim();
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (CATEGORY_CODES.contains(upper)) {
                return upper;
            }
            String labelCode = CATEGORY_LABELS.get(normalized);
            if (labelCode != null) {
                return labelCode;
            }
        }
        return CATEGORY_CODES.contains(fallback) ? fallback : "PUBLIC_WELFARE";
    }

    private LocalDateTime normalizeDateTime(Object value, String fallback) {
        String raw = StringUtils.hasText(text(value)) ? text(value) : fallback;
        String normalized = raw.trim().replace(' ', 'T');
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return inferStartTime("");
        }
    }

    private double normalizeDuration(Object value, double fallback) {
        double duration = number(value, fallback);
        if (duration < 0.5) duration = 0.5;
        if (duration > 24.0) duration = 24.0;
        return Math.round(duration * 10.0) / 10.0;
    }

    private int normalizeParticipants(Object value, int fallback) {
        int participants = integer(value, fallback);
        if (participants < 1) participants = 1;
        return Math.min(participants, 1000);
    }

    private String normalizeTags(Object tags, String fallback) {
        if (tags instanceof List<?> list) {
            String joined = list.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .limit(6)
                    .reduce((left, right) -> left + "," + right)
                    .orElse(fallback);
            return limit(joined, 255);
        }
        String tagText = text(tags);
        return limit(StringUtils.hasText(tagText) ? tagText : fallback, 255);
    }

    private double durationBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return Math.max(0.5, java.time.Duration.between(startTime, endTime).toMinutes() / 60.0);
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int integer(Object value, int fallback) {
        return (int) Math.round(number(value, fallback));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String formatDateTime(LocalDateTime value) {
        return DATE_TIME_FORMATTER.format(value);
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new HashMap<>() : value);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
