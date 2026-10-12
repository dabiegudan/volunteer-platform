package com.volunteer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 一体式志愿者服务平台启动入口。
 *
 * <p>Mapper 扫描使用 {@code com.volunteer.**.mapper} 通配，自动覆盖所有子模块的 mapper 包，
 * 新增模块无需修改此处配置。此前使用显式包列表，其中
 * {@code com.volunteer.ai.mapper} 与 {@code com.volunteer.server.mapper} 实际并不存在，
 * 属于隐性脆弱点（不同 MyBatis-Plus 版本对空包扫描的处理不一致）。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.volunteer.**.mapper")
public class VolunteerApplication {
    public static void main(String[] args) {
        SpringApplication.run(VolunteerApplication.class, args);
    }
}
