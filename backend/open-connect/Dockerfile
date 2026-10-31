FROM eclipse-temurin:25-jdk AS builder
LABEL maintainer="Pranit Bhangale"
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
RUN chmod +x gradlew

RUN ./gradlew --version --no-daemon

COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
LABEL maintainer="Pranit Bhangale"
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app

COPY --chown=spring:spring --from=builder /app/build/libs/*.jar app.jar

USER spring
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
