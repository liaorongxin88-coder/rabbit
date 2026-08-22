# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY config ./config
COPY rabbit-platform/pom.xml ./rabbit-platform/pom.xml
COPY rabbit-access/pom.xml ./rabbit-access/pom.xml
COPY rabbit-production/pom.xml ./rabbit-production/pom.xml
COPY rabbit-reporting/pom.xml ./rabbit-reporting/pom.xml
COPY rabbit-boot/pom.xml ./rabbit-boot/pom.xml
COPY rabbit-platform/src ./rabbit-platform/src
COPY rabbit-access/src ./rabbit-access/src
COPY rabbit-production/src ./rabbit-production/src
COPY rabbit-reporting/src ./rabbit-reporting/src
COPY rabbit-boot/src ./rabbit-boot/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl rabbit-boot -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/rabbit-boot/target/rabbit-backend.jar ./app.jar
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD ["bash", "-c", "</dev/tcp/127.0.0.1/8080"]
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
