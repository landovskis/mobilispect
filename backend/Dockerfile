# Build stage
FROM eclipse-temurin:21-jdk AS build

ARG APP_PATH=backend

WORKDIR /workspace

# Copy gradle wrapper and build files
COPY ${APP_PATH}/gradle ./gradle
COPY ${APP_PATH}/gradlew .
COPY ${APP_PATH}/gradle.properties .
COPY ${APP_PATH}/settings.gradle.kts .
COPY ${APP_PATH}/build.gradle.kts .
RUN chmod +x ./gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code and any supporting files
COPY ${APP_PATH}/ ./

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
