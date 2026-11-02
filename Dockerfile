# syntax=docker/dockerfile:1
# One build stage compiles every Spring Boot service; small runtime images are picked with --target.
#   docker build --target api .            -> GridPulse API
#   docker build --target notification .   -> notification service
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY notification-service/build.gradle notification-service/
COPY mcp-server/build.gradle mcp-server/
# dependencies first: this layer is reused until a build file changes
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
COPY notification-service/src notification-service/src
COPY mcp-server/src mcp-server/src
# tests run in CI and verify.sh; the image build only packages
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q bootJar \
 && mkdir /out \
 && cp build/libs/gridpulse-ai-*-SNAPSHOT.jar /out/api.jar \
 && cp notification-service/build/libs/notification-service-*-SNAPSHOT.jar /out/notification.jar

FROM eclipse-temurin:21-jre AS runtime-base
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
USER app
# container-aware heap: use up to 75% of the container's memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

FROM runtime-base AS api
COPY --from=build /out/api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS notification
COPY --from=build /out/notification.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
