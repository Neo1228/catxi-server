FROM eclipse-temurin:21-jdk-alpine AS source
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY module-api/build.gradle module-api/build.gradle
COPY module-worker/build.gradle module-worker/build.gradle
COPY module-contract/build.gradle module-contract/build.gradle
COPY gradle/libs.versions.toml gradle/libs.versions.toml
RUN chmod +x gradlew

COPY module-api/src module-api/src
COPY module-worker/src module-worker/src
COPY module-contract/src module-contract/src

FROM source AS api-build
RUN ./gradlew :module-api:bootJar --no-daemon

FROM source AS worker-build
RUN ./gradlew :module-worker:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring && \
    mkdir -p /app/logs && chown -R spring:spring /app
WORKDIR /app
USER spring:spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -T 3 -O /dev/null http://127.0.0.1:8081/actuator/health/liveness || exit 1

FROM runtime AS api
COPY --from=api-build --chown=spring:spring /workspace/module-api/build/libs/catxi-api.jar /app/app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS worker
COPY --from=worker-build --chown=spring:spring /workspace/module-worker/build/libs/catxi-worker.jar /app/app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
