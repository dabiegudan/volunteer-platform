package com.volunteer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <h2>为什么需要</h2>
 * <p>异步导出原本使用 {@code Executors.newFixedThreadPool(5)} 自建线程池，存在三个问题：
 * <ol>
 *   <li><b>无队列上限</b>：任务会无限排队，内存与磁盘可能被压垮；</li>
 *   <li><b>无优雅停机</b>：应用关闭时任务被中断，数据库中的任务状态停留在 PROCESSING；</li>
 *   <li><b>脱离 Spring 管理</b>：无法统一配置、无法监控、无法在测试中替换。</li>
 * </ol>
 * <p>改为 Spring 托管的 {@link ThreadPoolTaskExecutor} 后，线程数/队列容量可配置，
 * 且随应用优雅停机（{@code server.shutdown=graceful}）完成收尾。
 *
 * <h2>线程池参数取舍</h2>
 * <ul>
 *   <li>核心/最大线程数 2/8：导出属于 CPU + IO 混合型（POI 写 Excel 较吃 CPU），
 *       线程数不宜过多，避免与 Web 请求线程争抢；</li>
 *   <li>队列容量 100：超过后由调用线程执行（{@link ThreadPoolExecutor.CallerRunsPolicy}），
 *       形成天然背压，避免任务丢失或 OOM；</li>
 *   <li>线程名带 {@code export-} 前缀：便于日志与线程转储排查。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AsyncConfig {

    /** 导出任务专用线程池 Bean 名。 */
    public static final String EXPORT_EXECUTOR = "exportTaskExecutor";

    /**
     * 导出任务线程池。
     *
     * @return 线程池执行器
     */
    @Bean(name = EXPORT_EXECUTOR)
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("export-");
        // 队列满时由提交线程执行，形成背压；也保证任务不被静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待正在执行的任务完成，最多 120 秒
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        log.info("导出任务线程池已初始化: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }
}
