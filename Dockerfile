# Builds and runs Attendance Tracker in a container (used by Railway and other hosts).

# ---- 1. Build the jar with Maven (tests run on your computer; skipped here to keep deploys fast)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- 2. Run it on a small Java runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/attendance-tracker-1.0.0.jar app.jar

# "Today" must be the Indian date, whatever time zone the server is in.
ENV TZ=Asia/Kolkata
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata -XX:MaxRAMPercentage=75 -Djava.awt.headless=true"

# The host tells the app which port to use in the PORT variable (default 8080).
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
