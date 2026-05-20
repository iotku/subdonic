# Build
FROM gradle:9.5.1-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# Run (We want -jammy so we know we have a good glibc version for libdave)
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Springboot port
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
