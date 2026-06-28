FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ledgerstream \
    && useradd --system --gid ledgerstream --home-dir /app --shell /usr/sbin/nologin ledgerstream
COPY --from=build /workspace/target/backend-*.jar /app/backend.jar

ENV SPRING_PROFILES_ACTIVE=production
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER ledgerstream
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=5 CMD curl -fsS http://localhost:${SERVER_PORT:-8080}/actuator/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/backend.jar"]
