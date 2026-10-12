# 一体式志愿者服务平台

面向高校志愿服务场景的一体化平台，覆盖「创建活动 → 审核 → 发布 → 报名 → 组队 → 考勤 → 时长核算 → 激励 → 报表」的完整线上闭环，并可选接入大模型提供智能问答、活动推荐、内容生成与排班建议。

- **后端**：Spring Boot 3.3.2 多模块 Maven 工程（8 个模块，打成单个可执行 jar），JDK 17，MyBatis-Plus，MySQL 8，Redis 7，MinIO，Flyway，LangChain4j，ShedLock，SpringDoc
- **前端**：uni-app 3 + Vue 3.5 + Pinia + Vite（可编译 H5 与微信小程序）
- **交付**：Docker Compose 一键起全套依赖，配置集中在根目录 `.env`

---

## 快速开始

```bash
# 1. 准备配置（唯一需要填写的文件）
cp .env.example .env
./scripts/gen-secrets.ps1 -Apply        # 生成 JWT_SECRET / ENCRYPT_SECRET_KEY

# 2. 启动（推荐编排：MySQL + Redis + MinIO + 后端）
docker compose -f docker-compose.env.yml up -d --build

# 3. 验证
curl http://localhost:8080/api/actuator/health
```

首次启动由 MySQL 容器建库、由 Flyway 执行 `V1__baseline_full_schema.sql` 建表并写入种子数据。内置账号见《用户操作文档》，其中超级管理员为 `admin` / `Admin@2026Safe`。

> ⚠️ **种子账号口令属于「本地演示用默认凭据」，仅适用于绑定在 `127.0.0.1` 的本机实例。**
> 任何对外可访问的部署（公网、内网共享、演示环境）都必须先在登录后立即修改这些口令，
> 或删除这些账号；`V1__baseline_full_schema.sql` 的注释里也写明了「部署前请重新生成」。
> 详见 [SECRETS_REPORT.md](SECRETS_REPORT.md)。

若使用带 Nginx 的编排（`docker-compose.yml`，需自备 MySQL），还必须先构建前端产物：

```bash
cd volunteer-frontend
npm ci
npm run build:h5          # 生产构建走同源 /api，无需再设置 VITE_API_BASE_URL
cd ..
docker compose -f docker-compose.yml up -d --build
```

> 生产构建**不再把接口地址内联进产物**：`request.js` 在 `import.meta.env.PROD` 下使用同源
> 相对路径 `/api/**`，由 Nginx 反代到后端。因此同一份产物在任何域名/端口/子路径下都能直接工作，
> 也不会出现「浏览器把请求打向访问者自己的 localhost」这类线上故障。
> `VITE_API_BASE_URL` 只在开发构建（`npm run dev:h5`）下生效，默认 `http://localhost:8081`。

完整的部署步骤、配置项说明与故障排查见**《项目启动与配置指南》**。

### 本机开发（后端跑在宿主机）

不想每次开终端都先 `source .env`，可改用被 gitignore 的个人密钥文件：

```bash
cd volunteer-backend/volunteer-server/src/main/resources
cp application-local.yml.example application-local.yml   # 再把两个 CHANGE_ME 换成随机串
```

dev profile 会通过 `spring.config.import` 自动加载它。**注意**：`application.yml` / `application-dev.yml` 里
刻意**不再**写 `jwt.secret: ${JWT_SECRET:}` 这类空默认值 —— 导入的属性源优先级低于声明它的文档，
写了空值就会盖住本机密钥；环境变量 `JWT_SECRET` / `ENCRYPT_SECRET_KEY` 仍然有效且优先级最高。
详见《项目启动与配置指南》§2.6。

---

## 访问入口

| 入口 | 地址 |
|---|---|
| 前端页面（Nginx 编排） | `http://localhost/` |
| REST 接口 | `http://localhost/api/**`（直连后端为 `http://localhost:8080/api/**`） |
| Swagger UI | `http://localhost/swagger-ui/index.html` ← **不带 `/api`** |
| OpenAPI JSON | `http://localhost/v3/api-docs` ← **不带 `/api`** |
| 健康检查 | `http://localhost/api/actuator/health` |
| MinIO 控制台 | `http://localhost:9091` |

端口映射全部绑定回环地址：后端 `127.0.0.1:8080 → 容器 8081`、Redis `6379`、MinIO `9090 → 9000`（S3）与 `9091 → 9001`（控制台）、Nginx `80`（仅 `docker-compose.yml`）、MySQL `3306`（仅 `docker-compose.env.yml`）。

> `/api` 前缀来自各 Controller 的 `@RequestMapping`，项目**未设置** `server.servlet.context-path`；只有 Actuator 通过 `management.endpoints.web.base-path=/api/actuator` 对齐。

---

## 工程结构

```
volunteer-platform-v2/
├── volunteer-backend/                 后端（8 个 Maven 模块）
│   ├── volunteer-common/              统一返回体、异常、枚举、存储抽象、加密工具
│   ├── volunteer-auth/                JWT 认证、注解式授权、用户/角色/学院管理
│   ├── volunteer-activity/            活动生命周期、报名、组队、批量导入
│   ├── volunteer-attendance/          签到、位置校验、补签、失信、时长
│   ├── volunteer-notification/        站内信、公告、WebSocket 推送、短信抽象
│   ├── volunteer-report/              报表、统计、导出、信用、时间银行、技能、培训、课程表等
│   ├── volunteer-ai/                  LangChain4j：问答、推荐、草稿生成、排班建议
│   └── volunteer-server/              启动类、全部配置、文件接口（唯一含 resource 的模块）
├── volunteer-frontend/                前端（uni-app + Vue 3），源码在 src/
├── 文档/                              项目文档（见下）
├── scripts/                           .env 加载脚本与密钥生成脚本
├── docker-compose.yml                 Redis + MinIO + 后端 + Nginx（MySQL 自备）
├── docker-compose.env.yml             MySQL + Redis + MinIO + 后端（推荐）
├── Dockerfile                         后端两阶段构建
├── nginx.conf                         静态站点 + 反向代理 + WebSocket
├── .env / .env.example                运行时配置与其模板
└── pom.xml                            聚合 POM，统一依赖版本
```

后端模块依赖是单向无环的：`common` ← `auth` / `notification` / `ai` ← `activity` / `attendance` ← `report` ← `server`。

---

## 文档

| 文档 | 内容 |
|---|---|
| [一体式志愿者服务平台设计方案](文档/一体式志愿者服务平台设计方案.md) | 业务全景、活动状态机、架构与模块划分、技术选型与决策记要、核心模块设计、课程表模块、代码导读、术语表、已知缺口 |
| [数据库设计文档](文档/数据库设计文档.md) | 70 张表的字段/索引/外键/约束、ER 图、核心数据流转、种子数据、Flyway 迁移机制 |
| [后端接口API文档](文档/后端接口API文档.md) | 277 个端点的路由/参数/权限/返回、认证契约、统一响应体与异常映射、核心调用链 |
| [项目启动与配置指南](文档/项目启动与配置指南.md) | 两种部署路线、配置体系、必填变量、日常运维命令、故障排查 |
| [角色功能详细文档](文档/角色功能详细文档.md) | 权限模型三条判定链路、五个角色的能力边界与端点清单、数据可见性、已知缺口 |
| [用户操作文档](文档/用户操作文档.md) | 登录、绑定学号、报名、签到、查看时长的主流程与常见问题 |

---

## 环境要求

| 依赖 | 版本 | 用途 |
|---|---|---|
| Docker Desktop / Docker Engine + Compose v2 | Compose v2 | 容器化部署 |
| Node.js + npm | 18 LTS 及以上 | 构建前端产物 |
| JDK 17 | 17 | 本机开发（仓库自带 `mvnw`，无需另装 Maven） |

运行参数、端口、数据库与中间件配置**全部沿用原有约定**，未经改动；如需调整端口或凭据，只改根目录 `.env`，不要改 `application*.yml` 与编排文件中的默认结构。

---

## 环境变量配置

所有运行期配置只有两个来源：**根目录 `.env`**（容器路线）与**进程环境变量**（云/CI/K8s 路线）。
仓库里不存在任何真实密钥；`application*.yml` 与编排文件里只有 `${VAR}` 占位符与 `${VAR:?}` 必填校验。

### 1. 首次配置

```bash
cp .env.example .env                 # .env 已被 .gitignore 忽略，绝不会入库
./scripts/gen-secrets.ps1 -Apply     # 生成 JWT_SECRET / ENCRYPT_SECRET_KEY 写回 .env
# 再手动填 LLM_API_KEY（可选：留空不影响启动，只影响 /api/ai/**）
```

### 2. 变量分组

| 分组 | 变量 | 必填 | 说明 |
|---|---|---|---|
| 运行环境 | `SPRING_PROFILES_ACTIVE`、`SERVER_PORT`、`TZ` | 否 | 默认 `dev` / `8081` / `Asia/Shanghai` |
| MySQL | `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`SPRING_DATASOURCE_PASSWORD` | **是** | 编排文件用 `${VAR:?}` 校验；缺失即启动失败 |
| MySQL 连接 | `SPRING_DATASOURCE_URL`（容器形）、`SPRING_DATASOURCE_URL_HOST`（宿主机形） | **是** | 后者由 `docker-compose.yml` 使用 |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DB` | **密码必填** | 项目强制 Redis 鉴权，空密码不启动 |
| MinIO | `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`MINIO_ENDPOINT(_INTERNAL)` | **是** | 凭据与桶名；端点按运行形态二选一 |
| 安全密钥 | `JWT_SECRET`、`JWT_EXPIRATION`、`ENCRYPT_SECRET_KEY` | **是** | `JWT_SECRET` ≥ 32 字符，`ENCRYPT_SECRET_KEY` 固定 32 字符（AES-256） |
| 大模型 | `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`、`LLM_EMBEDDING_MODEL`、`LLM_TYPE`、`LLM_TIMEOUT` | Key 可选 | 留空不阻塞启动，仅在调用 `/api/ai/**` 时报错 |
| 迁移与日志 | `FLYWAY_*`、`LOGGING_LEVEL_*`、`SPRING_SERVLET_MULTIPART_*` | 否 | 均有默认值 |
| 前端 | `VITE_API_BASE_URL` | 否 | 只对开发构建 `npm run dev:h5` 生效；生产构建走同源 `/api` |

完整模板与逐条注释见 [`.env.example`](.env.example)；配置体系与故障排查见
[《项目启动与配置指南》](文档/项目启动与配置指南.md)。

### 3. 本机开发（不想用 `.env`）

见上文「[本机开发（后端跑在宿主机）](#本机开发后端跑在宿主机)」：把 `application-local.yml.example`
复制为 `application-local.yml`（已被 gitignore），dev profile 会通过 `spring.config.import` 自动加载。

### 4. 安全约定（提交前必读）

- **绝不提交** `.env`、`application-local.yml`、任何 `*.pem/*.key/*.p12/*.pfx/*.jks` 与真实密钥；
  `.gitignore` 与 `.dockerignore` 已覆盖，提交前务必用 `git status` 复核。
- **绝不把真实值写进** `application*.yml`、`docker-compose*.yml`、文档正文或 README 示例；
  需要示例时一律写 `your_api_key_here` / `change_me` 这类占位符。
- `VITE_*` 变量会被**打进前端公开产物**，因此永远不要在其中放密钥。
- 本机 `mvnw clean package` 产出的 jar 会带上本机 `application-local.yml`；对外分发请使用
  Docker 构建的镜像（`.dockerignore` 已排除该文件），或在打包前执行 `mvn clean`。
- 密钥一旦疑似外泄，**先轮换再清理历史**——重写 Git 历史不能替代轮换。
  已发现项、风险分级与轮换清单见 [SECRETS_REPORT.md](SECRETS_REPORT.md)。
