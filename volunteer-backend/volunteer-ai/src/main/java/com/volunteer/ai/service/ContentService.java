package com.volunteer.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {
    
    private final ChatModel chatModel;
    
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "暴力", "色情", "赌博", "毒品", "恐怖", "分裂", "反动",
            "邪教", "谣言", "辱骂", "歧视", "诈骗", "违法", "违规",
            "攻击", "煽动", "仇恨", "低俗", "淫秽", "血腥"
    );
    
    public String polishContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        try {
            String prompt = String.format(
                    "请帮我润色以下志愿者活动描述内容，使其更专业、更吸引人：\n\n%s\n\n要求：\n1. 保持原意不变\n2. 语言更流畅优美\n3. 突出活动亮点\n4. 适合在志愿者平台展示",
                    content
            );
            
            return chatModel.chat(prompt);
        } catch (Exception e) {
            log.warn("AI润色失败，返回原内容", e);
            return content;
        }
    }
    
    public List<String> detectSensitiveWords(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> foundWords = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        for (String word : SENSITIVE_WORDS) {
            if (lowerContent.contains(word.toLowerCase())) {
                foundWords.add(word);
            }
        }
        
        return foundWords;
    }
    
    public boolean containsSensitiveContent(String content) {
        return !detectSensitiveWords(content).isEmpty();
    }
    
    public Map<String, Object> analyzeContent(String content) {
        Map<String, Object> result = new HashMap<>();
        
        List<String> sensitiveWords = detectSensitiveWords(content);
        result.put("hasSensitiveContent", !sensitiveWords.isEmpty());
        result.put("sensitiveWords", sensitiveWords);
        
        if (!sensitiveWords.isEmpty()) {
            result.put("suggestion", "内容包含敏感词，请修改后再提交");
        } else {
            result.put("suggestion", "内容审核通过，可以发布");
        }
        
        return result;
    }
    
    public Map<String, Object> processContent(String content, boolean autoPolish) {
        Map<String, Object> result = new HashMap<>();
        
        List<String> sensitiveWords = detectSensitiveWords(content);
        result.put("hasSensitiveContent", !sensitiveWords.isEmpty());
        result.put("sensitiveWords", sensitiveWords);
        
        if (!sensitiveWords.isEmpty()) {
            result.put("status", "REJECTED");
            result.put("message", "内容包含敏感词，请修改后再提交");
            return result;
        }
        
        String finalContent = content;
        if (autoPolish) {
            finalContent = polishContent(content);
            result.put("polished", true);
        } else {
            result.put("polished", false);
        }
        
        result.put("content", finalContent);
        result.put("status", "APPROVED");
        result.put("message", "内容审核通过");
        
        return result;
    }
}
