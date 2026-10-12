package com.volunteer.auth.config;

import com.volunteer.auth.interceptor.AccessControlInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuthWebMvcConfig implements WebMvcConfigurer {
    
    private final AccessControlInterceptor accessControlInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessControlInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/captcha/**",
                    "/doc.html",
                    "/swagger-resources/**",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**"
                );
    }
}
