package com.volunteer.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Configuration
public class LangChain4jConfig {
    
    /**
     * 大模型 API 密钥。
     *
     * <p>默认值为空字符串，不提供任何内置密钥：密钥必须通过环境变量
     * {@code LLM_API_KEY} 注入（容器场景由 docker-compose 注入，本机场景由
     * scripts/load-env.ps1 导入）。缺失时应用仍可启动，但 {@code /api/ai/**}
     * 接口会在调用时失败，具体告警由 {@code com.volunteer.config.EnvConfig} 输出。
     */
    @Value("${llm.api-key:}")
    private String apiKey;
    
    @Value("${llm.base-url:https://api.deepseek.com}")
    private String baseUrl;
    
    @Value("${llm.model:deepseek-v4-flash}")
    private String model;
    
    @Value("${llm.embedding-model:text-embedding-ada-002}")
    private String embeddingModel;
    
    @Value("${llm.type:deepseek}")
    private String llmType;
    
    @Value("${llm.timeout:60000}")
    private int timeout;
    
    @Bean
    public ChatModel chatModel() {
        log.info("Initializing LLM Chat Model: type={}, model={}, baseUrl={}", llmType, model, baseUrl);

        // 只提示"未配置"，绝不打印密钥内容
        if (!StringUtils.hasText(apiKey)) {
            log.warn("LLM_API_KEY 未配置：/api/ai/** 接口将在调用时失败。"
                    + "请通过环境变量注入（容器由 docker-compose 注入，本机用 scripts/load-env.ps1）");
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(0.7)
                .timeout(Duration.ofMillis(timeout))
                .maxTokens(2048)
                .build();
    }
    
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Initializing Embedding Model: {}", embeddingModel);
        
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .timeout(Duration.ofMillis(timeout))
                .build();
    }
}
