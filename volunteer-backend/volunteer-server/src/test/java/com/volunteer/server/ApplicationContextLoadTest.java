package com.volunteer.server;

import com.volunteer.common.storage.FileStorage;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 上下文加载冒烟测试。
 *
 * <h2>为什么需要</h2>
 * <p>项目中已有的 {@code AllImplementedBackendEndpointSmokeTest} 使用
 * {@code MockMvcBuilders.standaloneSetup(...)} 构建，<b>不加载 Spring 上下文</b>，
 * 因此无法发现以下类型的问题：
 * <ul>
 *   <li>{@code @MapperScan} 配置错误（如扫描到不存在的包、通配符写法不被支持）；</li>
 *   <li>Bean 依赖缺失或循环依赖（如 ShedLock 的 {@code LockProvider} 未装配）；</li>
 *   <li>{@code @Value} 占位符无法解析（如新增无默认值的必填配置项）；</li>
 *   <li>组件扫描范围遗漏（如把配置类移出 {@code com.volunteer} 包）。</li>
 * </ul>
 * <p>本测试通过真实启动一次应用上下文来覆盖上述风险。
 *
 * <h2>测试环境</h2>
 * <p>使用 H2 内存库，避免依赖真实的 MySQL / Redis / MinIO。
 * 目的是验证 <b>Bean 接线</b>，而不是持久化行为（后者由各模块的单元测试覆盖）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // H2 内存库，MySQL 兼容模式
                "spring.datasource.url=jdbc:h2:mem:contextload;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.hikari.connection-test-query=SELECT 1",
                // Redis：仅验证 Bean 装配，不实际连接
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                // 定时任务不纳入本测试
                "spring.task.scheduling.enabled=false",
                // 缩短启动日志噪音
                "logging.level.root=WARN",
                "logging.level.com.volunteer=WARN"
        }
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // 提供必填配置项，验证「缺失即失败」之外的正向路径
        "jwt.secret=context-load-test-secret-key-at-least-32-characters-long",
        "encrypt.secret-key=context-load-test-aes-key-32chars",
        "llm.api-key=context-load-test-llm-key",
        /*
         * 关键：必须显式关闭 Flyway。
         *
         * 本测试用的是 H2 内存库，但 spring.flyway.enabled 默认 true，
         * 若不关闭，Flyway 会尝试在 H2 上执行 db/migration 下的 MySQL 专用 DDL
         * （utf8mb4、ENGINE=InnoDB、CHECK 约束等）而失败；更严重的是，
         * 一旦有人把 datasource 改回真实库，测试启动就会**在真实库上执行迁移**，
         * 造成测试污染生产/开发数据。因此任何加载完整上下文的测试都必须显式关闭它。
         */
        "spring.flyway.enabled=false"
})
class ApplicationContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private FileStorage fileStorage;

    @Test
    @DisplayName("Spring 上下文可正常加载")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("ShedLock LockProvider 已装配（分布式定时任务锁生效的前提）")
    void shedLockProviderIsWired() {
        assertThat(lockProvider)
                .as("LockProvider 缺失会导致 @SchedulerLock 全部失效，多副本下任务重复执行")
                .isNotNull();
    }

    @Test
    @DisplayName("FileStorage 已装配（异步导出产物写入对象存储的前提）")
    void fileStorageIsWired() {
        assertThat(fileStorage)
                .as("FileStorage 缺失会导致导出产物无法写入对象存储")
                .isNotNull();
    }

    @Test
    @DisplayName("@MapperScan 通配生效：所有 MyBatis Mapper 均已注册为 Bean")
    void allMappersAreRegistered() {
        // 取各业务模块的一个代表性 Mapper 进行断言
        String[] expectedMapperNames = {
                "activityMapper",              // volunteer-activity
                "attendanceMapper",            // volunteer-attendance
                "sysUserMapper",               // volunteer-auth
                "notifyUserMessageMapper",     // volunteer-notification
                "exportTaskMapper",            // volunteer-report
                "sysOperationLogMapper"        // volunteer-common
        };

        Map<String, org.apache.ibatis.session.SqlSessionFactory> sqlSessionFactories =
                applicationContext.getBeansOfType(org.apache.ibatis.session.SqlSessionFactory.class);
        assertThat(sqlSessionFactories).as("SqlSessionFactory 未装配").isNotEmpty();

        for (String mapperName : expectedMapperNames) {
            assertThat(applicationContext.containsBean(mapperName))
                    .as("@MapperScan(\"com.volunteer.**.mapper\") 未覆盖 Mapper: %s", mapperName)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("关键配置类已加载")
    void keyConfigurationsLoaded() {
        assertThat(applicationContext.containsBean("schedulerLockConfig"))
                .as("SchedulerLockConfig 未加载：@SchedulerLock 不会生效")
                .isTrue();
        assertThat(applicationContext.containsBean("asyncConfig"))
                .as("AsyncConfig 未加载：@Async(\"exportTaskExecutor\") 会回退到默认线程池")
                .isTrue();
        assertThat(applicationContext.containsBean("minioConfig"))
                .as("MinioConfig 未加载：MinIO 客户端无法构建")
                .isTrue();
    }
}
