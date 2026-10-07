
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 sgp
WORKDIR /app
COPY --from=build /workspace/target/sgp-api-0.0.1-SNAPSHOT.jar app.jar
USER sgp
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
