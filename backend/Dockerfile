ARG JDK_BASE_IMAGE=eclipse-temurin:21-jdk-jammy
ARG JRE_BASE_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${JDK_BASE_IMAGE} AS build

ARG MAVEN_REPO_URL=https://repo.maven.apache.org/maven2

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN if [ "$MAVEN_REPO_URL" != "https://repo.maven.apache.org/maven2" ]; then \
        mkdir -p /root/.m2 \
        && sed "s|__MAVEN_REPO_URL__|$MAVEN_REPO_URL|g" .mvn/docker-settings.xml > /root/.m2/settings.xml; \
    fi
RUN sh ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN sh ./mvnw -q -DskipTests package

FROM ${JRE_BASE_IMAGE}

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/IntelligentOutfitRecommendationSystem-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=30s \
    CMD curl --fail --silent http://localhost:${SERVER_PORT:-8080}/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
