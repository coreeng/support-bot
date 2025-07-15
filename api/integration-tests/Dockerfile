FROM gradle:8.11.1-jdk21 AS builder
WORKDIR /home/gradle/project

RUN curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Copy gradle wrapper and settings
COPY gradlew .
COPY settings.gradle.kts .
COPY gradle gradle

RUN ./gradlew --no-daemon

COPY integration-tests/build.gradle.kts integration-tests/build.gradle.kts
COPY integration-tests/src integration-tests/src

RUN ./gradlew :integration-tests:jar --no-daemon

FROM registry.access.redhat.com/ubi8/openjdk-21:1.21
WORKDIR /app

COPY --from=builder /usr/local/bin/helm /usr/local/bin/helm

COPY --from=builder /home/gradle/project/integration-tests/build/libs/integration-tests.jar .

COPY k8s/service k8s/service

ENTRYPOINT ["java", "-jar", "integration-tests.jar", "execute", "-p", "com.coreeng.supportbot"]