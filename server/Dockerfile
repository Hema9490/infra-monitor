# Build context is the repository root:  docker build -f server/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY server/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY server/src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/infra-monitor-server.jar app.jar
COPY scripts /app/scripts
RUN chmod +x /app/scripts/*.sh /app/scripts/*.py
ENV SCRIPTS_DIR=/app/scripts
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
