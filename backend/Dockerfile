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

USER ledgerstream
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
