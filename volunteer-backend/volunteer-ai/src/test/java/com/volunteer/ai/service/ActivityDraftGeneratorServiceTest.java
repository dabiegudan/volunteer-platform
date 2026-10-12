package com.volunteer.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityDraftGeneratorService 单元测试")
class ActivityDraftGeneratorServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("解析带代码块的AI JSON并规范化字段")
    void parseFencedJson() {
        when(chatModel.chat(anyString())).thenReturn("""
                ```json
                {
                  "title": "社区环保清洁行动",
                  "location": "大学城社区广场",
                  "category": "环保行动",
                  "startTime": "2026-06-16T14:00:00",
                  "endTime": "2026-06-16T17:00:00",
                  "durationHours": 3,
                  "maxParticipants": 32,
                  "leaderName": "演示组织者",
                  "description": "组织志愿者开展社区清洁和垃圾分类宣传。",
                  "tags": ["环保", "社区"],
                  "notificationTitle": "社区环保清洁行动招募通知",
                  "notificationContent": "小伙伴们大家好！\\n社区环保清洁行动开始招募志愿者啦！\\n\\n🔔志愿保障：\\n1️⃣提供志愿服务时长。"
                }
                ```
                """);
        ActivityDraftGeneratorService service = new ActivityDraftGeneratorService(chatModel, new ObjectMapper());

        Map<String, Object> draft = service.generateDraft("下周下午做社区环保清洁", Map.of());

        assertThat(draft.get("title")).isEqualTo("社区环保清洁行动");
        assertThat(draft.get("category")).isEqualTo("ENVIRONMENT");
        assertThat(draft.get("organizerName")).isEqualTo("演示组织者");
        assertThat(draft.get("durationHours")).isEqualTo(3.0);
        assertThat(draft.get("notificationTitle")).isEqualTo("社区环保清洁行动招募通知");
        assertThat(draft.get("notificationContent")).asString().contains("志愿保障");
        assertThat(draft.get("aiGenerated")).isEqualTo(true);
    }

    @Test
    @DisplayName("模型异常时返回本地兜底草稿")
    void fallbackWhenModelFails() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("model offline"));
        ActivityDraftGeneratorService service = new ActivityDraftGeneratorService(chatModel, new ObjectMapper());

        Map<String, Object> draft = service.generateDraft("给老人教智能手机", Map.of("organizerName", "组织者"));

        assertThat(draft.get("title")).asString().contains("助老");
        assertThat(draft.get("location")).isEqualTo("大学城社区党群服务中心");
        assertThat(draft.get("organizerName")).isEqualTo("组织者");
        assertThat(draft.get("notificationContent")).asString().contains("小伙伴们大家好").contains("报名链接");
        assertThat(draft.get("aiGenerated")).isEqualTo(false);
    }
}
