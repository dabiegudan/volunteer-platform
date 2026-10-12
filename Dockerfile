# 一体式志愿者服务平台 —— 后端镜像
# 构建上下文为项目根目录（需要根 pom.xml 与 mvnw）：
#   docker build -t volunteer-backend:2.3.0 .
#   docker compose -f docker-compose.env.yml build backend
# 运行期不含明文密钥，全部通过环境变量注入；应用监听 8081。

# 构建阶段
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# 1) 只复制依赖描述文件，最大化利用 Docker 层缓存
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY volunteer-backend/volunteer-common/pom.xml      volunteer-backend/volunteer-common/
COPY volunteer-backend/volunteer-auth/pom.xml        volunteer-backend/volunteer-auth/
COPY volunteer-backend/volunteer-activity/pom.xml    volunteer-backend/volunteer-activity/
COPY volunteer-backend/volunteer-attendance/pom.xml  volunteer-backend/volunteer-attendance/
COPY volunteer-backend/volunteer-notification/pom.xml volunteer-backend/volunteer-notification/
COPY volunteer-backend/volunteer-report/pom.xml      volunteer-backend/volunteer-report/
COPY volunteer-backend/volunteer-ai/pom.xml          volunteer-backend/volunteer-ai/
COPY volunteer-backend/volunteer-server/pom.xml      volunteer-backend/volunteer-server/

# 2) Windows 换行符会导致 mvnw 无法执行，先归一化
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# 3) 预拉取依赖（此层在仅修改源码时命中缓存）
RUN ./mvnw -B dependency:go-offline

# 4) 复制源码并打包
COPY volunteer-backend/volunteer-common/src      volunteer-backend/volunteer-common/src
COPY volunteer-backend/volunteer-auth/src        volunteer-backend/volunteer-auth/src
COPY volunteer-backend/volunteer-activity/src    volunteer-backend/volunteer-activity/src
COPY volunteer-backend/volunteer-attendance/src  volunteer-backend/volunteer-attendance/src
COPY volunteer-backend/volunteer-notification/src volunteer-backend/volunteer-notification/src
COPY volunteer-backend/volunteer-report/src      volunteer-backend/volunteer-report/src
COPY volunteer-backend/volunteer-ai/src          volunteer-backend/volunteer-ai/src
COPY volunteer-backend/volunteer-server/src      volunteer-backend/volunteer-server/src

RUN ./mvnw -B clean package -DskipTests

# 统一产物文件名，避免版本号变更导致 COPY 失败
RUN cp volunteer-backend/volunteer-server/target/volunteer-server-*.jar /build/app.jar

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 时区（与 .env 的 TZ、JDBC serverTimezone 保持一致）
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

# 非 root 运行，降低容器被攻破后的影响面
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --from=builder --chown=app:app /build/app.jar app.jar

USER app

EXPOSE 8081

# 容器感知的 JVM 参数：按容器内存上限动态分配堆
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# 健康检查（容器内已安装 curl）；探针路径可通过 HEALTH_CHECK_PATH 覆盖
ENV HEALTH_CHECK_PATH=/api/actuator/health
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8081}${HEALTH_CHECK_PATH}" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
