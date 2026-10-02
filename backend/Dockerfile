# syntax=docker/dockerfile:1.7
# WAVE API (Kotlin / Ktor). Build: docker build -t wave/backend backend/
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
# Dependencies first: this layer is reused until the build files change.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin wave
WORKDIR /app
COPY --from=build --chown=root:root /src/build/install/wave-backend /app
USER 10001
# ZGC and MaxRAMPercentage come from the start script; a broken heap must restart the pod.
ENV WAVE_ENV=prod \
    LOG_APPENDER=JSON \
    JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080 9090
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/health/live || exit 1
ENTRYPOINT ["/app/bin/wave-backend"]
