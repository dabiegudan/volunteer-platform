package com.volunteer.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 分布式定时任务锁配置。
 *
 * <h2>为什么需要</h2>
 * <p>项目中的 {@code @Scheduled} 任务原本没有任何互斥机制，应用一旦以多副本运行，
 * 每个副本都会执行同一次调度，导致：
 * <ul>
 *   <li>报名截止提醒、名额紧张提醒被<b>重复推送</b>；</li>
 *   <li>活动状态归档被<b>重复执行</b>。</li>
 * </ul>
 * <p>ShedLock 通过数据库中的一张锁表（{@code shedlock}）保证同一任务在同一时刻
 * 只被一个实例执行，从而解除「应用只能单副本运行」的限制。
 *
 * <h2>锁定语义</h2>
 * <ul>
 *   <li>{@code lockAtMostFor}：任务持锁的最长时间。超过后锁自动释放，
 *       防止实例崩溃导致任务永久阻塞。必须大于任务的最坏执行耗时。</li>
 *   <li>{@code lockAtLeastFor}：任务持锁的最短时间。用于抑制短时间内多次触发
 *       （如时钟抖动、手动重复触发），避免重复执行。</li>
 * </ul>
 * <p>锁表使用<b>数据库时间</b>（{@code usingDbTime()}）作为时钟源，
 * 避免多实例间系统时钟偏差导致锁失效。
 *
 * <h2>兼容性</h2>
 * <p>使用 ShedLock 5.16.0：其 shedlock-parent 对应 spring 6.1.12 / spring-boot 3.3.3 / jdk 17，
 * 与本项目的 Spring Boot 3.3.2 严格对齐。
 * ShedLock 6.x 要求 Spring Boot 3.4+，7.x 要求 Spring Boot 4.x + Spring 7，均不可用。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    /**
     * 基于 JDBC 的锁提供者。
     *
     * <p>选用数据库实现而非 Redis 实现的原因：项目本身已强依赖 MySQL，
     * 不引入额外组件；且锁必须与业务数据保持同源可靠。
     *
     * @param dataSource Spring 管理的连接池（HikariCP）
     * @return 锁提供者实例
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        .usingDbTime()
                        .build()
        );
    }
}
