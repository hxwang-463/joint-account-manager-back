# syntax=docker/dockerfile:1

# ---- Stage 1: build ----------------------------------------------------------
# Pinned to the *builder's* native architecture. Java bytecode is portable, so
# the jar is built once natively and reused by every target platform instead of
# recompiling under QEMU emulation for each one.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached unless the pom changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# Explode the fat jar into Spring Boot layers so that application code changes
# do not invalidate the (large, slow-moving) dependency layers.
RUN cp target/*.jar app.jar && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Non-root user. Running as root inside a container is flagged by most scanners
# and buys nothing here.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
