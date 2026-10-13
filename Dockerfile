# Builds and runs Attendance Tracker in a container (used by Render and other hosts).

# ---- 1. Build the jar with Maven (tests run on your computer; skipped here to keep deploys fast)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- 2. Run it on a small Java runtime image (JRE only, no compiler or Maven)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/attendance-tracker-1.0.0.jar app.jar

# "Today" must be the Indian date, whatever time zone the server is in.
ENV TZ=Asia/Kolkata
# Render's free plan gives the whole container 512 MB. Measured on this app: ~60 MB of live data,
# ~85 MB of class metadata and ~25 MB of compiled code, so the budget below stays under 512 MB:
#   heap 50% = 256 MB + metaspace 140 MB + code 40 MB + direct buffers 32 MB + threads (40 x 512 KB)
# If memory still runs out, the app exits cleanly and Render restarts it (instead of freezing).
# To change these on Render, set JAVA_TOOL_OPTIONS in the dashboard (it replaces this whole line).
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata -XX:MaxRAMPercentage=50 -XX:MaxMetaspaceSize=140m -XX:ReservedCodeCacheSize=40m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"

# The host tells the app which port to use in the PORT variable (default 8080).
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
