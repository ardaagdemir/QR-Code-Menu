# --- Build stage ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Gap-analysis #15: MediaStoragePort's local adapter writes under /data/media (backed by
# the media_data volume, infra/docker-compose.yml) - must be owned by the non-root
# runtime user before the volume mount initializes from it, or every upload 500s with
# AccessDeniedException.
RUN useradd --system --create-home appuser \
    && mkdir -p /data/media \
    && chown -R appuser:appuser /data/media
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
