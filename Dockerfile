# ─── Build stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache Maven deps separately from source changes
COPY .mvn/      .mvn/
COPY mvnw       pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Extract Spring Boot layered jar for optimal Docker layer caching
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layers in dependency order (rarely-changed first = better layer cache hit rate)
COPY --from=builder /build/target/extracted/dependencies/          ./
COPY --from=builder /build/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/           ./

# curl for health check
RUN apk add --no-cache curl

USER appuser

EXPOSE 8080

# UseContainerSupport: JVM respects cgroup memory limits inside Docker
# MaxRAMPercentage: use 75% of container memory for heap
# virtual threads are enabled in application.yml
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+OptimizeStringConcat", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
