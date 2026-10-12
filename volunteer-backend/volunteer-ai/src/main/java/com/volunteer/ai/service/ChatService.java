package com.volunteer.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
            你是一体式志愿者服务平台的AI助手"小愿"。
            你的职责：解答关于志愿活动报名、服务时长、积分规则等问题。

            常见问题参考：
            1. 报名流程：用户可以通过平台首页的"活动列表"选择心仪的活动，点击"立即报名"按钮进行报名
            2. 服务时长计算：志愿者签到后开始计时，签退时结束计时，系统自动计算有效服务时长
            3. 积分规则：每服务1小时获得1积分，积分可兑换礼品或参与抽奖
            4. 活动参与要求：年满18周岁，身体健康，遵守活动纪律

            回复要求：
            - 语气热情简洁
            - 回答准确、有帮助
            - 不确定时请建议咨询管理员
            - 使用中文回答
            """;

    public String chat(String userMessage) {
        try {
            String normalizedMessage = normalizeMessage(userMessage);
            if (normalizedMessage.isEmpty()) {
                return "请先输入您想咨询的问题。";
            }

            String prompt = SYSTEM_PROMPT + "\n\n用户问题：" + normalizedMessage;
            String response = chatModel.chat(prompt);
            String answer = normalizeResponse(response);
            log.debug("AI问答完成，用户问题: {}, 回答长度: {}", preview(normalizedMessage), answer.length());
            return answer;
        } catch (Exception e) {
            log.error("AI问答失败", e);
            return "很抱歉，我暂时无法回答您的问题，请稍后再试。";
        }
    }

    public String chatWithHistory(String userMessage, List<Map<String, String>> history) {
        try {
            String normalizedMessage = normalizeMessage(userMessage);
            if (normalizedMessage.isEmpty()) {
                return "请先输入您想咨询的问题。";
            }

            StringBuilder promptBuilder = new StringBuilder(SYSTEM_PROMPT);

            if (history != null) {
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if ("user".equals(role)) {
                        promptBuilder.append("\n用户：").append(content);
                    } else if ("assistant".equals(role)) {
                        promptBuilder.append("\n助手：").append(content);
                    }
                }
            }

            promptBuilder.append("\n用户：").append(normalizedMessage);
            String response = chatModel.chat(promptBuilder.toString());
            String answer = normalizeResponse(response);
            log.debug("AI带历史对话完成，用户问题: {}, 历史消息数: {}", preview(normalizedMessage), history != null ? history.size() : 0);
            return answer;
        } catch (Exception e) {
            log.error("AI带历史对话失败", e);
            return "很抱歉，我暂时无法回答您的问题，请稍后再试。";
        }
    }

    public Map<String, Object> getFAQs() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> faqs = new ArrayList<>();

        faqs.add(createFAQ("如何报名参加志愿活动？", "您可以通过平台首页的\"活动列表\"选择心仪的活动，点击\"立即报名\"按钮进行报名。报名成功后，您会收到系统通知。"));
        faqs.add(createFAQ("服务时长如何计算？", "志愿者在活动开始前签到后开始计时，活动结束后签退时结束计时，系统会自动计算有效服务时长。"));
        faqs.add(createFAQ("积分规则是什么？", "每服务1小时获得1积分，积分可以在积分商城兑换礼品或参与抽奖活动。"));
        faqs.add(createFAQ("参加活动有什么要求？", "年满18周岁，身体健康，能够遵守活动纪律和组织者的安排。"));
        faqs.add(createFAQ("如何查看我的服务记录？", "您可以在\"我的\"页面中查看\"服务记录\"，里面包含您所有的志愿服务历史。"));

        result.put("faqs", faqs);
        return result;
    }

    private Map<String, String> createFAQ(String question, String answer) {
        Map<String, String> faq = new HashMap<>();
        faq.put("question", question);
        faq.put("answer", answer);
        return faq;
    }

    private String normalizeMessage(String message) {
        return message == null ? "" : message.trim();
    }

    private String normalizeResponse(String response) {
        if (response == null || response.isBlank()) {
            return "我暂时没有生成回答，请换个问题再试。";
        }
        return response;
    }

    private String preview(String message) {
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }
}
