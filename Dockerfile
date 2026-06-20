# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/lycanclaw-backend-0.0.1-SNAPSHOT.jar app.jar
RUN groupadd --system lycan \
    && useradd --system --gid lycan --home-dir /app --shell /usr/sbin/nologin lycan \
    && mkdir -p /app/data \
    && chown -R lycan:lycan /app
USER lycan
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
