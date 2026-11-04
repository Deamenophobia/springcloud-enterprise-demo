# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY framework-labs/pom.xml framework-labs/pom.xml
COPY security-common/pom.xml security-common/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY security-common/src security-common/src
COPY gateway/src gateway/src
COPY catalog-service/src catalog-service/src
COPY order-service/src order-service/src
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 test -n "$SERVICE" && mvn -B -ntp -pl "$SERVICE" -am -DskipTests package && cp "$SERVICE/target/$SERVICE-1.0.0-SNAPSHOT.jar" /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=10001:10001 /app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 9090
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 CMD curl --fail --silent http://localhost:9090/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
