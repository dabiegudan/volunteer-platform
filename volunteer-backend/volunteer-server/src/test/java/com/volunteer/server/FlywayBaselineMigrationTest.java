package com.volunteer.server;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Flyway 基线（Baseline）方案的两条路径验证。
 *
 * <h2>为什么需要这个测试</h2>
 * <p>基线方案的<b>核心卖点在「已有数据库」这条路径上</b>：库非空且无历史表时，
 * Flyway 应写入一条版本 = {@code baseline-version} 的基线记录，并
 * <b>跳过基线及之前的脚本</b>，只执行之后的增量。这条路径如果配错
 * （例如 {@code baseline-version} 设成 0），会在已有库上重复执行
 * {@code V1__baseline_full_schema.sql}，导致
 * {@code Table 'xxx' already exists} 并使应用无法启动。
 *
 * <p>仅靠「空库能跑通」无法发现该问题，因此本测试显式构造两种初始状态：
 * <ol>
 *   <li><b>场景一（空库）</b>：不预置任何表 → 期望执行 V1 基线，建出全部业务表与种子数据；</li>
 *   <li><b>场景二（已有库）</b>：先执行 {@code db/init.sql} 造出与基线等价的库
 *       → 期望建立基线、跳过 V1，且<b>已有数据不丢</b>。</li>
 * </ol>
 *
 * <p>另有场景三验证幂等性，场景四在临时目录动态生成一个<b>版本号高于仓库内全部脚本</b>的
 * 增量脚本，验证「基线之后的增量链路」可用 —— 版本号动态取号而非写死，
 * 因此仓库里后续继续新增 V3、V4 … 也不会与本测试撞版本。
 *
 * <h2>依赖外部 MySQL</h2>
 * <p>本测试需要可用的 MySQL（默认 {@code localhost:3306}，账号 {@code root/root}）。
 * 环境不可用时通过 {@link EnabledIf} 与 {@code assumeThat} 自动跳过，
 * 不会导致 CI 失败。可用以下环境变量覆盖连接信息：
 * {@code TEST_MYSQL_HOST}、{@code TEST_MYSQL_PORT}、
 * {@code TEST_MYSQL_USER}、{@code TEST_MYSQL_PASSWORD}。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(
        expression = "${TEST_MYSQL_ENABLED:true}",
        loadContext = false,
        reason = "需要本地 MySQL；设置 TEST_MYSQL_ENABLED=false 可跳过"
)
class FlywayBaselineMigrationTest {

    /** 隔离的验证库，不与开发库 volunteer_db 冲突。 */
    private static final String SCHEMA_FRESH = "volunteer_migration_fresh";
    private static final String SCHEMA_EXISTING = "volunteer_migration_existing";

    private static final String HOST = env("TEST_MYSQL_HOST", "localhost");
    private static final String PORT = env("TEST_MYSQL_PORT", "3306");
    private static final String USER = env("TEST_MYSQL_USER", "root");
    private static final String PASSWORD = env("TEST_MYSQL_PASSWORD", "root");

    /**
     * 不指定目标库的连接，用于建库/删库以及访问 {@code information_schema}。
     *
     * <p>显式连接到 {@code mysql} 系统库而非使用空库名 URL
     * （{@code jdbc:mysql://host:3306/?...}）——空库名在部分驱动/服务端组合下
     * 会导致会话未落到预期库，进而影响库级 DDL 的执行。
     */
    private static String serverUrl() {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/mysql?useUnicode=true&characterEncoding=utf8"
                + "&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    }

    /** 指定库的连接。 */
    private static String schemaUrl(String schema) {
        return "jdbc:mysql://" + HOST + ":" + PORT + "/" + schema
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    }

    private static DriverManagerDataSource dataSource(String schema) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(schemaUrl(schema));
        ds.setUsername(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }

    /** 与 application.yml 中 spring.flyway.* 保持一致的 Flyway 构造。 */
    private static Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(dataSource(schema))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("baseline full schema")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .table("flyway_schema_history")
                .encoding(java.nio.charset.StandardCharsets.UTF_8)
                .load();
    }

    private static boolean mysqlAvailable() {
        try (Connection ignored = new DriverManagerDataSource(serverUrl(), USER, PASSWORD)
                .getConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void dropAndCreate(String schema, JdbcTemplate jdbc) {
        jdbc.execute("DROP DATABASE IF EXISTS " + schema);
        jdbc.execute("CREATE DATABASE " + schema
                + " DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");
    }

    private static int tableCount(JdbcTemplate jdbc, String schema) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                Integer.class, schema);
        return n == null ? 0 : n;
    }

    private static List<Map<String, Object>> history(JdbcTemplate jdbc, String schema) {
        return jdbc.queryForList(
                "SELECT installed_rank, version, description, type, success "
                        + "FROM " + schema + ".flyway_schema_history ORDER BY installed_rank");
    }

    @BeforeAll
    static void checkMysql() {
        assumeThat(mysqlAvailable())
                .as("本地 MySQL 不可用，跳过 Flyway 迁移验证（可用 TEST_MYSQL_* 覆盖连接信息）")
                .isTrue();
    }

    @AfterAll
    static void cleanup() {
        if (!mysqlAvailable()) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(serverUrl(), USER, PASSWORD));
        for (String schema : new String[]{SCHEMA_FRESH, SCHEMA_EXISTING}) {
            try {
                jdbc.execute("DROP DATABASE IF EXISTS " + schema);
            } catch (Exception ignored) {
                // 清理失败不影响测试结论
            }
        }
    }

    // ========================================================================
    // 场景一：全新空库 —— 应执行 V1 基线，建出全量结构
    // ========================================================================
    @Test
    @Order(1)
    @DisplayName("场景一：空库执行 V1 基线，建出 70 张表与种子数据")
    void freshDatabaseRunsBaselineMigration() {
        /*
         * 注意：这里必须"先建空库再让 Flyway 迁移"。
         *
         * 库本身必须先存在 —— Flyway 的 create-schemas 救不了这个场景：
         * 它在取得 JDBC 连接之后才执行创建，而库不存在时 MySQL 驱动在连接阶段
         * 就抛 "Unknown database"，反编译 Flyway.java:164 可见该逻辑顺序。
         * 因此真实部署时也需要先 CREATE DATABASE（Docker 方式由 MYSQL_DATABASE 代劳）。
         */
        JdbcTemplate admin = new JdbcTemplate(
                new DriverManagerDataSource(serverUrl(), USER, PASSWORD));
        dropAndCreate(SCHEMA_FRESH, admin);

        Flyway flyway = flyway(SCHEMA_FRESH);
        var result = flyway.migrate();

        // 1) 迁移链：V1（基线）+ V2（签到凭据绑定表），因此应执行 2 个脚本
        assertThat(result.migrationsExecuted)
                .as("空库应执行迁移链中的全部脚本（V1 基线 + V2 脚本）")
                .isEqualTo(2);
        assertThat(result.success).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource(SCHEMA_FRESH));

        // 2) 核心结构就位：71 张业务表 + 1 张历史表
        assertThat(tableCount(jdbc, SCHEMA_FRESH))
                .as("空库迁移后应有 72 张表（71 业务表 + flyway_schema_history）")
                .isEqualTo(72);

        // 3) 种子数据就位（验证 V1 里的 INSERT 确实执行了）
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA_FRESH + ".activity_category", Integer.class))
                .as("活动分类种子应有 11 条")
                .isEqualTo(11);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA_FRESH + ".sys_user", Integer.class))
                .as("内置账号应有 8 个")
                .isEqualTo(8);

        // 4) 主键自增正常（证明 AUTO_INCREMENT 未被破坏）
        Integer maxId = jdbc.queryForObject(
                "SELECT MAX(id) FROM " + SCHEMA_FRESH + ".sys_permission", Integer.class);
        assertThat(maxId)
                .as("sys_permission 主键应为自增生成的 1..15")
                .isEqualTo(15);

        // 5) 历史表应记录迁移链，且无 BASELINE 记录
        //    （空库走的是"执行脚本"路径，不是"建立基线"路径）
        List<Map<String, Object>> rows = history(jdbc, SCHEMA_FRESH);
        assertThat(rows).isNotEmpty();
        // 注意：MySQL 的 success 列映射为 Boolean，不是数字
        assertThat(rows).allSatisfy(r ->
                assertThat((Boolean) r.get("success")).isTrue());
        assertThat(rows.get(0).get("version")).hasToString("1");
        assertThat(rows.get(0).get("type"))
                .as("空库执行的应是 SQL 脚本而非 BASELINE 记录")
                .hasToString("SQL");
        assertThat(rows)
                .as("空库路径下不应出现任何 BASELINE 记录")
                .noneSatisfy(r -> assertThat(r.get("type")).hasToString("BASELINE"));
    }

    // ========================================================================
    // 场景二：已有库 —— 应建立基线、跳过 V1，数据不丢
    // ========================================================================
    @Test
    @Order(2)
    @DisplayName("场景二：已有库自动建立基线，跳过 V1，且数据不丢")
    void existingDatabaseIsBaselinedAndSkipsV1() {
        JdbcTemplate admin = new JdbcTemplate(
                new DriverManagerDataSource(serverUrl(), USER, PASSWORD));
        dropAndCreate(SCHEMA_EXISTING, admin);

        // ---- 步骤 1：构造"已有数据库" ----
        // 真实场景中的"已有库"具备两个特征：库里有完整的业务表，但没有 flyway_schema_history
        // （因为此前项目未接入 Flyway）。这里用最贴近该形态的方式构造：
        //   ① 先让 Flyway 建出全量结构（等价于此前用 init.sql 手工建的库）
        //   ② 删掉 flyway_schema_history，抹去迁移记录 —— 此时库"看起来"就是一个
        //      从未被 Flyway 管理过、但已经装满表的存量库
        // 这样避免了重放 init.sql 带来的 SQL 切分/权限等无关干扰因素。
        Flyway setup = flyway(SCHEMA_EXISTING);
        setup.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource(SCHEMA_EXISTING));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA_EXISTING + ".sys_user", Integer.class))
                .as("构造阶段应写入 8 个内置账号")
                .isEqualTo(8);

        // 插入一条业务数据，用于验证基线迁移不会破坏已有数据
        jdbc.execute("INSERT INTO " + SCHEMA_EXISTING
                + ".activity_category (category_name, category_code, icon, description, sort_order, status)"
                + " VALUES ('迁移验证分类', 'MIGRATION_PROBE', 'test', '验证基线迁移不丢数据', 99, 1)");

        // 抹掉历史表 —— 此时库成为"非空且无历史记录"的存量库，正是基线方案的触发条件
        jdbc.execute("DROP TABLE " + SCHEMA_EXISTING + ".flyway_schema_history");
        int tablesBefore = tableCount(jdbc, SCHEMA_EXISTING);
        assertThat(tablesBefore)
                .as("构造完成后应有 71 张业务表，且无历史表")
                .isEqualTo(71);

        // ---- 步骤 2：对已有库执行迁移，验证基线行为 ----
        Flyway flyway = flyway(SCHEMA_EXISTING);
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted)
                .as("基线版本(1)及之前的脚本必须被跳过。注意 V2 在基线之上，"
                        + "因此本场景允许执行 V2；若此处把 V1 也执行了，会报 Table already exists")
                .isLessThanOrEqualTo(1);

        // ---- 步骤 3：断言历史表内容 ----
        // 第一条记录必须是 BASELINE（版本 1，未执行脚本）
        List<Map<String, Object>> rows = history(jdbc, SCHEMA_EXISTING);
        Map<String, Object> baseline = rows.stream()
                .filter(r -> "BASELINE".equals(String.valueOf(r.get("type"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "存量库必须走 BASELINE 路径；未找到 BASELINE 记录说明 V1 被真正执行了"));

        assertThat(baseline.get("version")).hasToString("1");
        assertThat(baseline.get("description")).hasToString("baseline full schema");
        assertThat((Boolean) baseline.get("success")).isTrue();

        // V1 的建表语句绝不能被重放（重放会因 Table already exists 直接失败）。
        // 用显式过滤 + 断言，而不是 noneSatisfy 里塞复合条件 —— 后者在断言失败时
        // 会把全部元素都列为"满足要求"，报错信息具有误导性。
        List<String> scriptedVersions = rows.stream()
                .filter(r -> "SQL".equals(String.valueOf(r.get("type"))))
                .map(r -> String.valueOf(r.get("version")))
                .toList();
        assertThat(scriptedVersions)
                .as("历史表中不应存在 version=1 的 SQL 记录（V1 必须被基线跳过）")
                .doesNotContain("1");

        // ---- 步骤 4：数据完整性 ----
        assertThat(tableCount(jdbc, SCHEMA_EXISTING))
                .as("迁移后表数应为 71 张业务表 + 历史表 = 72")
                .isEqualTo(72);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA_EXISTING + ".sys_user", Integer.class))
                .as("已有用户数据不应丢失")
                .isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA_EXISTING
                        + ".activity_category WHERE category_code = 'MIGRATION_PROBE'", Integer.class))
                .as("基线迁移不应删除已有业务数据")
                .isEqualTo(1);
    }

    // ========================================================================
    // 场景四：增量迁移链路 —— 基线之后的增量脚本应被正常执行
    // ========================================================================
    @Test
    @Order(4)
    @DisplayName("场景四：基线之后新增的增量脚本应被正常执行")
    void incrementalMigrationAfterBaselineIsApplied(@TempDir Path tempDir) throws Exception {
        /*
         * 本用例验证"基线之后的增量链路"确实可用。
         *
         * 仓库内已有 V1（基线）与若干真实增量脚本，因此这里在临时目录中动态写一个
         * **版本号高于仓库内所有脚本**的增量脚本，并把 Flyway 的 locations 配成
         * 「classpath:db/migration + 临时目录」。这样无需在仓库里保留一个只为测试存在的
         * 示例脚本，也能完整覆盖增量路径。
         *
         * 【必须动态取号】早期实现固定使用 V2：一旦仓库真的新增 V2，classpath 与临时目录
         * 就会同时出现两个 version=2 的脚本，Flyway 直接报
         * "Found more than one migration with version 2" 并让整个测试失败。
         */
        String probeVersion = nextAvailableVersion();
        Path migrationDir = tempDir.resolve("migration");
        Files.createDirectories(migrationDir);
        Files.writeString(
                migrationDir.resolve("V" + probeVersion + "__incremental_probe.sql"),
                "ALTER TABLE activity_category "
                        + "ADD COLUMN migration_probe_marker VARCHAR(32) NULL "
                        + "COMMENT 'Flyway 增量链路验证字段';\n",
                StandardCharsets.UTF_8);

        JdbcTemplate admin = new JdbcTemplate(
                new DriverManagerDataSource(serverUrl(), USER, PASSWORD));
        dropAndCreate(SCHEMA_FRESH, admin);

        // locations 同时含 classpath 与临时目录
        // 注意：Flyway 的 filesystem location 需要显式 "filesystem:" 前缀，
        // 传 file:///... 的 URI 会报 "Unknown prefix for location"。
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource(SCHEMA_FRESH))
                .locations("classpath:db/migration", "filesystem:" + migrationDir.toAbsolutePath())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted)
                .as("应执行 V1 基线与仓库内全部增量脚本，外加临时目录的探针脚本")
                .isGreaterThanOrEqualTo(2);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource(SCHEMA_FRESH));

        // 增量脚本的效果确实落地了
        Integer columnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'activity_category' "
                        + "AND column_name = 'migration_probe_marker'",
                Integer.class, SCHEMA_FRESH);
        assertThat(columnCount)
                .as("探针脚本新增的列应存在，证明增量脚本被真正执行")
                .isEqualTo(1);

        // 历史表应记录 V1（SQL）与探针版本（SQL）两条，且全部成功
        List<Map<String, Object>> rows = history(jdbc, SCHEMA_FRESH);
        assertThat(rows).allSatisfy(r ->
                assertThat((Boolean) r.get("success")).isTrue());
        assertThat(rows.get(rows.size() - 1).get("version")).hasToString(probeVersion);
    }

    // ========================================================================
    // 场景三：幂等性 —— 重复执行迁移不应产生任何变更
    // ========================================================================
    @Test
    @Order(3)
    @DisplayName("场景三：重复执行迁移是幂等的，不应有新脚本被执行")
    void repeatedMigrateIsIdempotent() {
        // 承接场景二留下的库（历史表中已有 BASELINE(1) 与真实增量 V2 两条记录）。
        // 注意：不能断言"历史表只有 1 条" —— 仓库里存在 V2 之后，
        // 存量库建基线的同时必然还会执行 V2，历史表天然是 2 条。
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(SCHEMA_EXISTING));
        int historyBefore = history(jdbc, SCHEMA_EXISTING).size();

        Flyway flyway = flyway(SCHEMA_EXISTING);
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted)
                .as("已是最新版本时不应执行任何脚本")
                .isZero();

        assertThat(history(jdbc, SCHEMA_EXISTING))
                .as("历史表记录数不应增加")
                .hasSize(historyBefore);
    }

    // ========================================================================
    // 场景零：脚本读取与切分自检（纯本地，不依赖数据库）
    // ========================================================================
    @Test
    @Order(0)
    @DisplayName("场景零：db/init.sql 可被读取并正确切分为 71 条 CREATE TABLE")
    void initSqlCanBeReadAndSplit() throws Exception {
        String sql = readInitSql();

        assertThat(sql)
                .as("classpath 下的 db/init.sql 不应为空（检查 maven-resources 过滤配置）")
                .isNotBlank();
        assertThat(sql)
                .as("脚本应含建库语句")
                .contains("DROP DATABASE IF EXISTS volunteer_db");

        List<String> statements = splitSql(sql);
        long createTable = statements.stream()
                .map(String::trim)
                .filter(s -> s.toUpperCase().startsWith("CREATE TABLE"))
                .count();

        assertThat(statements.size())
                .as("脚本切分后的语句总数")
                .isGreaterThan(150);
        assertThat(createTable)
                .as("切分后 CREATE TABLE 语句数应为 71"
                        + "（V1 基线建 70 张 + init.sql 额外含 V2 的 attendance_credential）")
                .isEqualTo(71);

        // 语句中不应残留注释行导致的碎片
        assertThat(statements)
                .as("切分结果不应含只有注释的碎片语句")
                .noneSatisfy(s -> assertThat(s.trim()).startsWith("--"));
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /**
     * 计算"仓库内尚不存在的下一个版本号"，用于场景四动态生成的增量探针脚本。
     *
     * <p>【为什么必须动态取号】早期实现固定写死 {@code V2__...}。仓库后来真的新增了
     * {@code V2__attendance_credential_binding.sql} 之后，classpath 与临时目录就同时存在
     * 两个 version=2 的脚本，Flyway 直接抛
     * {@code Found more than one migration with version 2}，
     * 使整个测试类失败 —— 一个只为测试存在的脚本名，把测试变成了一颗定时炸弹。
     *
     * <p>这里扫描 classpath 的 {@code db/migration} 目录，取所有 {@code V<数字>__} 前缀的
     * 最大版本号并 +1；扫描失败或目录为空时回退到 2。
     */
    private static String nextAvailableVersion() {
        int max = 1;
        try {
            var url = FlywayBaselineMigrationTest.class
                    .getClassLoader().getResource("db/migration");
            if (url != null && "file".equals(url.getProtocol())) {
                Path dir = Paths.get(url.toURI());
                try (var stream = Files.list(dir)) {
                    max = stream.map(p -> p.getFileName().toString())
                            .map(n -> {
                                var m = java.util.regex.Pattern
                                        .compile("^V(\\d+)__.*\\.sql$", java.util.regex.Pattern.CASE_INSENSITIVE)
                                        .matcher(n);
                                return m.matches() ? Integer.parseInt(m.group(1)) : 0;
                            })
                            .max(Integer::compareTo)
                            .orElse(1);
                }
            }
        } catch (Exception ignored) {
            // 资源扫描失败不应让测试失败，回退即可
        }
        return String.valueOf(max + 1);
    }

    /** 读取 db/init.sql 原文（用于校验资源是否可用、脚本是否可被正确切分）。 */
    private static String readInitSql() throws Exception {
        try (var in = FlywayBaselineMigrationTest.class
                .getClassLoader().getResourceAsStream("db/init.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "classpath 下找不到 db/init.sql，请确认资源未被过滤");
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * 按分号切分 SQL，跳过注释与字符串内的分号。
     *
     * <p>MySQL 的 {@code --} 注释、{@code /* *}{@code /} 块注释以及单引号字符串
     * 都可能含分号，直接 split(";") 会切错脚本。
     */
    private static List<String> splitSql(String sql) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inSingle = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                    buf.append(ch);
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingle && ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (!inSingle && ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                // 处理 '' 转义
                if (inSingle && next == '\'') {
                    buf.append(ch).append(next);
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                buf.append(ch);
                continue;
            }
            if (ch == ';' && !inSingle) {
                out.add(buf.toString());
                buf.setLength(0);
                continue;
            }
            buf.append(ch);
        }
        if (buf.toString().trim().length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }
}
