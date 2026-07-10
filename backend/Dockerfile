FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd --system skybooker && useradd --system --gid skybooker skybooker
COPY --from=build /workspace/target/skybooker-backend-*.jar /app/skybooker-backend.jar
RUN chown -R skybooker:skybooker /app

USER skybooker
EXPOSE 8080

# issue #139: 默认 JVM 业务时区设为 Asia/Shanghai，与 DATETIME 存储的北京时间语义一致。
# compose 可通过 JAVA_OPTS 环境变量覆盖，但需自行保证业务时区一致。
ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai"
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -fsS 'http://localhost:8080/api/flights?page=1&size=1' >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/skybooker-backend.jar"]
