package com.volunteer.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.volunteer.common.interceptor.EncryptInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 与 application.yml 中的 com.mysql.cj.jdbc.Driver 保持一致
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 敏感字段（{@code @Encrypted}）透明加解密插件。
     *
     * <p>历史实现上该类虽然带 {@code @Component}，但<b>从未被注册到 MyBatis 的
     * InterceptorChain</b> —— 既没有被 {@code MybatisPlusInterceptor} 收集，
     * 也没有任何 {@code Configuration.addInterceptor} 调用，因此解密逻辑是死代码：
     * 身份证号一旦写入就永远以密文原样返回给前端。这里显式声明为 Bean，
     * 由 MyBatis-Plus 的自动配置统一注册，读写两侧同时生效。</p>
     */
    @Bean
    public EncryptInterceptor encryptInterceptor() {
        return new EncryptInterceptor();
    }
}
