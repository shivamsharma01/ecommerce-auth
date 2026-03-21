# syntax=docker/dockerfile:1
# Build stage
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle wrapper and build config (cached unless changed)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached unless build.gradle/settings.gradle change)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source and build
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Extract layers for efficient caching (Spring Boot layered JAR)
WORKDIR /app/extracted
RUN JAR=$(ls /app/build/libs/*.jar | grep -v plain) && \
    java -Djarmode=layertools -jar "$JAR" extract

# Runtime stage
FROM eclipse-temurin:17-jre-jammy AS runtime

# Create non-root user
RUN groupadd --gid 1000 app && \
    useradd --uid 1000 --gid app --shell /bin/false --create-home app

WORKDIR /app

# Copy extracted layers (dependencies first for better cache reuse)
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

USER app

EXPOSE 8081

# JVM tuning for containers and fast startup
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
