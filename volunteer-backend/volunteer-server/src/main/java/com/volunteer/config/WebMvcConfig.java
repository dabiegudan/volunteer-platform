package com.volunteer.config;

import com.volunteer.auth.interceptor.ProfileCompletenessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RateLimitInterceptor rateLimitInterceptor;
    private final ProfileCompletenessInterceptor profileCompletenessInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/login")
                .addPathPatterns("/api/activity/**")
                .addPathPatterns("/api/attendance/**");

        // #2 志愿者档案完整度门控
        registry.addInterceptor(profileCompletenessInterceptor)
                .addPathPatterns("/api/activity/**")
                .addPathPatterns("/api/attendance/**");
    }
}
