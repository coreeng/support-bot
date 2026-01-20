FROM gradle:8.12.1-jdk21 AS builder
WORKDIR /home/gradle/project

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Copy Gradle wrapper and core build configuration
COPY gradlew .
COPY settings.gradle.kts .
COPY gradle gradle
COPY buildSrc buildSrc

# Warm up Gradle to prime the wrapper and caches
RUN ./gradlew --no-daemon

# Copy only the subprojects needed to build the functional tests jar
COPY testkit testkit
COPY functional/lombok.config functional/lombok.config
COPY functional/build.gradle.kts functional/build.gradle.kts
COPY functional/src functional/src

# Build runnable fat jar that includes tests and all deps
RUN ./gradlew :functional:jar --no-daemon

FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:1.23
WORKDIR /app

# Copy tests jar
COPY --from=builder /home/gradle/project/functional/build/libs/functional.jar ./functional-tests.jar

ENTRYPOINT ["java", "-jar", "functional-tests.jar", "execute", "-p", "com.coreeng.supportbot"]
USER 185
