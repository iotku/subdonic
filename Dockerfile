# Build
FROM gradle:8.9-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Run 
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Springboot port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
