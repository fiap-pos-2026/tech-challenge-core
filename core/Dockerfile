# syntax=docker/dockerfile:1
ARG PROJECT=core

FROM eclipse-temurin:25-jdk AS build

ARG PROJECT

WORKDIR /workspace/app

COPY . .

RUN --mount=type=cache,target=/root/.gradle chmod +x ./gradlew && ./gradlew ${PROJECT}:build -x test

RUN mkdir -p ${PROJECT}/build/dependency && (cd ${PROJECT}/build/dependency; jar -xf ../libs/*.jar)

FROM eclipse-temurin:25-jre-alpine AS runtime

ARG PROJECT

ARG DEPENDENCY=/workspace/app/${PROJECT}/build/dependency

RUN apk upgrade --no-cache \
    && apk add --no-cache wget unzip \
    && addgroup -S -g 10001 nonroot \
    && adduser -S -D -H -u 10001 -G nonroot nonroot \
    && mkdir -p /newrelic \
    && wget -q https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip -O /tmp/nr.zip \
    && unzip -q /tmp/nr.zip -d /tmp \
    && cp /tmp/newrelic/newrelic.jar /newrelic/newrelic.jar \
    && chown -R 10001:10001 /newrelic \
    && rm -rf /tmp/nr.zip /tmp/newrelic \
    && apk del wget unzip

COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib

COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF

COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

COPY ${PROJECT}/docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod 755 /app/docker-entrypoint.sh && chown 10001:10001 /app/docker-entrypoint.sh

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["/app/docker-entrypoint.sh"]
