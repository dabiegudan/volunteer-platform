package com.volunteer.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryGeneratorService {
    
    private final ChatModel chatModel;
    
    public Map<String, Object> generateSummary(String activityName, String keywords, String activityType, Integer serviceHours) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String prompt = String.format("""
                    你是一个志愿者活动总结助手。请根据以下信息帮志愿者生成一份活动心得总结：
                    
                    活动名称：%s
                    活动类型：%s
                    服务时长：%s小时
                    关键词：%s
                    
                    请生成一篇结构清晰、内容充实的活动总结，包括：
                    1. 活动概述
                    2. 个人收获与体会
                    3. 反思与感悟
                    4. 未来展望
                    
                    要求：
                    - 语言流畅自然，符合志愿者分享风格
                    - 内容积极向上，体现志愿服务精神
                    - 结合关键词展示活动亮点
                    - 字数约300-500字
                    """, activityName, activityType != null ? activityType : "公益活动", 
                    serviceHours != null ? serviceHours : "未知", keywords);
            
            String summary = chatModel.chat(prompt);
            
            result.put("success", true);
            result.put("summary", summary);
            result.put("activityName", activityName);
            result.put("keywords", keywords);
            result.put("wordCount", summary.length());
            
            log.debug("活动总结生成完成，活动: {}, 字数: {}", activityName, summary.length());
            
        } catch (Exception e) {
            log.error("活动总结生成失败", e);
            result.put("success", false);
            result.put("summary", "很抱歉，暂时无法生成活动总结，请稍后再试。");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> generateMultipleSummaries(String activityName, List<String> keywordGroups) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            StringBuilder summaries = new StringBuilder();
            
            for (int i = 0; i < keywordGroups.size(); i++) {
                Map<String, Object> summaryResult = generateSummary(activityName, keywordGroups.get(i), null, null);
                if ((Boolean) summaryResult.get("success")) {
                    summaries.append(String.format("【版本%d】\n", i + 1));
                    summaries.append(summaryResult.get("summary")).append("\n\n");
                }
            }
            
            result.put("success", true);
            result.put("summaries", summaries.toString());
            result.put("count", keywordGroups.size());
            
        } catch (Exception e) {
            log.error("批量生成活动总结失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> generateReflection(String activityName, String personalExperience) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String prompt = String.format("""
                    你是一个志愿者成长导师。请根据志愿者的活动经历，帮助他/她进行深度反思和总结：
                    
                    活动名称：%s
                    个人经历描述：%s
                    
                    请从以下角度帮助志愿者进行反思：
                    1. 这次活动中我学到了什么？
                    2. 我遇到了哪些挑战，是如何克服的？
                    3. 我为他人带来了什么价值？
                    4. 未来我可以如何改进和提升？
                    
                    请用亲切、鼓励的语气，帮助志愿者发现成长点和改进方向。
                    """, activityName, personalExperience);
            
            String reflection = chatModel.chat(prompt);
            
            result.put("success", true);
            result.put("reflection", reflection);
            result.put("activityName", activityName);
            
            log.debug("反思总结生成完成，活动: {}", activityName);
            
        } catch (Exception e) {
            log.error("反思总结生成失败", e);
            result.put("success", false);
            result.put("reflection", "很抱歉，暂时无法生成反思总结，请稍后再试。");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> generateCertificateContent(String activityName, String volunteerName, 
                                                          Integer serviceHours, String date) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String prompt = String.format("""
                    你是一个证书内容撰写助手。请为志愿者撰写志愿服务证书内容：
                    
                    活动名称：%s
                    志愿者姓名：%s
                    服务时长：%s小时
                    服务日期：%s
                    
                    请撰写一段正式、庄重的证书内容，包括：
                    1. 对志愿者参与的肯定
                    2. 志愿服务精神的赞扬
                    3. 颁发证书的机构名称（可虚拟）
                    4. 日期
                    
                    要求语言正式、简洁、得体。
                    """, activityName, volunteerName, serviceHours != null ? serviceHours : "未知", 
                    date != null ? date : "");
            
            String content = chatModel.chat(prompt);
            
            result.put("success", true);
            result.put("content", content);
            result.put("activityName", activityName);
            result.put("volunteerName", volunteerName);
            
            log.debug("证书内容生成完成，志愿者: {}, 活动: {}", volunteerName, activityName);
            
        } catch (Exception e) {
            log.error("证书内容生成失败", e);
            result.put("success", false);
            result.put("content", "很抱歉，暂时无法生成证书内容，请稍后再试。");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}