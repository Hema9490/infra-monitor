# Build context is the repository root:  docker build -f agent/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY agent/pom.xml .
COPY agent/src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/infra-monitor-agent.jar agent.jar
ENTRYPOINT ["java", "-jar", "/app/agent.jar"]
