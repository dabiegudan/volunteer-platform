package com.volunteer.ai.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {
    
    private final ChatModel chatModel;
    
    private static final Set<String> POSITIVE_WORDS = Set.of(
            "好", "棒", "优秀", "满意", "喜欢", "精彩", "完美", "开心", "感谢", "赞",
            "不错", "很好", "出色", "给力", "推荐", "支持", "帮助", "温暖", "感动",
            "快乐", "幸福", "顺利", "成功", "积极", "热情", "友好", "认真", "负责",
            "奉献", "爱心", "善良", "真诚", "耐心", "细心", "专业", "专注"
    );
    
    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "差", "糟糕", "不满意", "失望", "烂", "差强人意", "不行", "失败",
            "问题", "麻烦", "困难", "投诉", "批评", "差评", "态度差", "效率低",
            "不负责", "不认真", "敷衍", "推脱", "迟到", "混乱", "无序", "浪费",
            "抱怨", "消极", "抵触", "冷漠", "自私", "懒惰", "粗心", "马虎"
    );

    public SentimentResult analyze(String text) {
        if (text == null || text.isEmpty()) {
            return new SentimentResult("NEUTRAL", 0.5, "文本为空", Collections.emptyList(), Collections.emptyList());
        }
        
        int positiveCount = 0;
        int negativeCount = 0;
        List<String> positiveWordsFound = new ArrayList<>();
        List<String> negativeWordsFound = new ArrayList<>();
        
        String lowerText = text.toLowerCase();
        
        for (String word : POSITIVE_WORDS) {
            if (lowerText.contains(word)) {
                positiveCount++;
                positiveWordsFound.add(word);
            }
        }
        
        for (String word : NEGATIVE_WORDS) {
            if (lowerText.contains(word)) {
                negativeCount++;
                negativeWordsFound.add(word);
            }
        }
        
        int total = positiveCount + negativeCount;
        double score;
        
        if (total == 0) {
            score = 0.5;
        } else {
            score = (double) positiveCount / total;
        }
        
        String sentiment;
        if (score > 0.6) {
            sentiment = "POSITIVE";
        } else if (score < 0.4) {
            sentiment = "NEGATIVE";
        } else {
            sentiment = "NEUTRAL";
        }
        
        String analysis = generateAnalysis(sentiment, score, text);
        
        return new SentimentResult(sentiment, score, analysis, positiveWordsFound, negativeWordsFound);
    }
    
    public Map<String, Object> analyzeApplication(String applicationText, String activityCategory) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            SentimentResult sentimentResult = analyze(applicationText);
            
            String prompt = String.format("""
                    你是一个志愿者申请审核助手。请分析以下志愿者的报名申请理由：
                    
                    申请理由：%s
                    
                    活动分类：%s
                    
                    请从以下几个方面进行分析：
                    1. 动机真实性：评估申请人参与动机的真实性和真诚度
                    2. 匹配度分析：评估申请人与活动的匹配程度
                    3. 能力评估：评估申请人是否具备参与活动的基本能力
                    4. 建议：给出审核建议（通过/待定/拒绝）及理由
                    
                    请输出结构化的分析报告，包括：综合评分（0-100分）、各维度评分、详细评语。
                    """, applicationText, activityCategory);
            
            String aiAnalysis = chatModel.chat(prompt);
            
            Map<String, Object> aiResult = parseAIAnalysis(aiAnalysis);
            
            result.put("sentiment", sentimentResult.getSentiment());
            result.put("sentimentScore", sentimentResult.getScore());
            result.put("positiveWords", sentimentResult.getPositiveWords());
            result.put("negativeWords", sentimentResult.getNegativeWords());
            result.put("sentimentAnalysis", sentimentResult.getAnalysis());
            result.put("aiAnalysis", aiAnalysis);
            result.put("overallScore", aiResult.get("overallScore"));
            result.put("motivationScore", aiResult.get("motivationScore"));
            result.put("matchScore", aiResult.get("matchScore"));
            result.put("abilityScore", aiResult.get("abilityScore"));
            result.put("suggestion", aiResult.get("suggestion"));
            result.put("comments", aiResult.get("comments"));
            
            log.debug("申请审核分析完成，文本长度: {}, 综合评分: {}", applicationText.length(), aiResult.get("overallScore"));
            
        } catch (Exception e) {
            log.error("申请审核分析失败", e);
            SentimentResult sentimentResult = analyze(applicationText);
            result.put("sentiment", sentimentResult.getSentiment());
            result.put("sentimentScore", sentimentResult.getScore());
            result.put("positiveWords", sentimentResult.getPositiveWords());
            result.put("negativeWords", sentimentResult.getNegativeWords());
            result.put("sentimentAnalysis", sentimentResult.getAnalysis());
            result.put("aiAnalysis", "AI分析服务暂时不可用");
            result.put("overallScore", Math.round(sentimentResult.getScore() * 100));
            result.put("motivationScore", Math.round(sentimentResult.getScore() * 100));
            result.put("matchScore", 50);
            result.put("abilityScore", 50);
            result.put("suggestion", "待定");
            result.put("comments", "建议人工审核");
        }
        
        return result;
    }
    
    public Map<String, Object> batchAnalyzeApplications(List<Map<String, String>> applications) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> analysisResults = new ArrayList<>();
        
        for (Map<String, String> app : applications) {
            String text = app.get("text");
            String activityCategory = app.get("activityCategory");
            String applicantId = app.get("applicantId");
            
            Map<String, Object> analysis = analyzeApplication(text, activityCategory);
            analysis.put("applicantId", applicantId);
            analysisResults.add(analysis);
        }
        
        analysisResults.sort((a, b) -> {
            Integer scoreA = (Integer) a.get("overallScore");
            Integer scoreB = (Integer) b.get("overallScore");
            return scoreB.compareTo(scoreA);
        });
        
        result.put("applications", analysisResults);
        result.put("totalCount", analysisResults.size());
        result.put("passCount", analysisResults.stream().filter(r -> "通过".equals(r.get("suggestion"))).count());
        result.put("pendingCount", analysisResults.stream().filter(r -> "待定".equals(r.get("suggestion"))).count());
        result.put("rejectCount", analysisResults.stream().filter(r -> "拒绝".equals(r.get("suggestion"))).count());
        
        return result;
    }
    
    private String generateAnalysis(String sentiment, double score, String text) {
        StringBuilder analysis = new StringBuilder();
        
        if ("POSITIVE".equals(sentiment)) {
            analysis.append("文本整体情感积极，表达了正面的态度和意愿。");
        } else if ("NEGATIVE".equals(sentiment)) {
            analysis.append("文本整体情感偏消极，可能存在不满或负面情绪。");
        } else {
            analysis.append("文本情感中性，需要结合上下文进一步分析。");
        }
        
        analysis.append(String.format(" 情感得分：%.2f", score));
        
        return analysis.toString();
    }
    
    private Map<String, Object> parseAIAnalysis(String aiAnalysis) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("overallScore", 75);
        result.put("motivationScore", 70);
        result.put("matchScore", 80);
        result.put("abilityScore", 75);
        result.put("suggestion", "通过");
        result.put("comments", aiAnalysis);
        
        try {
            if (aiAnalysis.contains("综合评分")) {
                int start = aiAnalysis.indexOf("综合评分") + 4;
                int end = aiAnalysis.indexOf("分", start);
                if (end > start) {
                    result.put("overallScore", Integer.parseInt(aiAnalysis.substring(start, end).trim()));
                }
            }
            
            if (aiAnalysis.contains("通过")) {
                result.put("suggestion", "通过");
            } else if (aiAnalysis.contains("拒绝")) {
                result.put("suggestion", "拒绝");
            } else if (aiAnalysis.contains("待定") || aiAnalysis.contains("待审核")) {
                result.put("suggestion", "待定");
            }
        } catch (Exception e) {
            log.warn("解析AI分析结果失败", e);
        }
        
        return result;
    }
    
    public static class SentimentResult {
        private final String sentiment;
        private final double score;
        private final String analysis;
        private final List<String> positiveWords;
        private final List<String> negativeWords;
        
        public SentimentResult(String sentiment, double score, String analysis, 
                              List<String> positiveWords, List<String> negativeWords) {
            this.sentiment = sentiment;
            this.score = score;
            this.analysis = analysis;
            this.positiveWords = positiveWords;
            this.negativeWords = negativeWords;
        }
        
        public SentimentResult(String sentiment, double score) {
            this(sentiment, score, "", Collections.emptyList(), Collections.emptyList());
        }
        
        public String getSentiment() {
            return sentiment;
        }
        
        public double getScore() {
            return score;
        }
        
        public String getAnalysis() {
            return analysis;
        }
        
        public List<String> getPositiveWords() {
            return positiveWords;
        }
        
        public List<String> getNegativeWords() {
            return negativeWords;
        }
    }
}