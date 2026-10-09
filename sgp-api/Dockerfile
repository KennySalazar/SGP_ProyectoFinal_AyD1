# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
ARG BUILD_DATE="unknown"
ARG VCS_REF="unknown"
ARG VERSION="development"

LABEL org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/KennySalazar/SGP_ProyectoFinal_AyD1" \
      org.opencontainers.image.title="SGP API" \
      org.opencontainers.image.version="${VERSION}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 sgp
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/sgp-api-*.jar app.jar
USER sgp
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8090/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
