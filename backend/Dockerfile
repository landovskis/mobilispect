# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle/libs.versions.toml gradle/

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew assemble --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /workspace/build/libs/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
