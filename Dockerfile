# Multi-stage Docker build for Distributed Library Management System
# Stage 1: Build & Package
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Production Lightweight Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/*.jar app.jar

ENV PORT=8081
EXPOSE 8081

# Container RAM optimization for cloud deployments (e.g. Render 512MB tier)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
