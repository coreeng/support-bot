FROM gradle:9.3.1-jdk25 AS builder
WORKDIR /home/gradle/project

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

COPY gradlew .
COPY settings.gradle.kts .
COPY gradle gradle
COPY buildSrc buildSrc

# Warm up Gradle wrapper and download plugins/dependencies metadata
RUN ./gradlew --no-daemon help

COPY testkit testkit
COPY nft nft

# Pre-compile NFT sources and resolve dependencies so most work happens at
# image build time. Using gatlingClasses ensures both Gatling Scala sources and
# resources are built, so runtime only has to execute the simulation, not
# recompile it.
#
# We also fully resolve the Gatling runtime classpath (including dynamic
# versions and all required artifacts) so that the runtime container can run
# Gradle in --offline mode using the cached metadata and jars, and finally
# relax permissions so the non-root runtime user can copy the artifacts.
RUN ./gradlew :nft:gatlingClasses :nft:warmupGatlingRuntimeClasspath --no-daemon \
  && chmod -R a+rX /home/gradle/project /home/gradle/.gradle

FROM registry.access.redhat.com/ubi9/openjdk-25:1.24
WORKDIR /app

COPY --from=builder /home/gradle/project /opt/project
COPY --from=builder /home/gradle/.gradle /opt/gradle-home

ENV GRADLE_USER_HOME=/tmp/gradle-home

ENV NFT_SIMULATION_FQCN="com.coreeng.supportbot.nft.TicketFlowSimulation"

# ENTRYPOINT flow:
#   1. Copy the pre-built project into /tmp/project (writable under the K8s
#      emptyDir volume) so Gradle and Gatling can write build and report output.
#   2. Copy the Gradle user home from the builder stage into /tmp/gradle-home
#      so runtime Gradle can reuse dependency caches while still writing only
#      under /tmp.
#   3. Run the Gatling Gradle task in non-interactive, offline mode for the NFT
#      simulation and then copy the generated reports into the PVC-backed
#      /mnt/nft-reports directory so they can be exported by the helper pod.
ENTRYPOINT ["sh", "-c", "\
  set -e; \
  mkdir -p /tmp/project \"$GRADLE_USER_HOME\" /mnt/nft-reports; \
  cp -R /opt/project/. /tmp/project/; \
  if [ -d /opt/gradle-home ]; then \
    cp -R /opt/gradle-home/. \"$GRADLE_USER_HOME/\"; \
  fi; \
  cd /tmp/project; \
  CI=true ./gradlew :nft:gatlingRun --no-daemon --offline --simulation=$NFT_SIMULATION_FQCN || GRADLE_EXIT_CODE=$?; \
  : \"${GRADLE_EXIT_CODE:=0}\"; \
  rm -rf /mnt/nft-reports/*; \
  if [ -d /tmp/project/nft/build/reports/gatling ]; then \
    cp -R /tmp/project/nft/build/reports/gatling/. /mnt/nft-reports/ || true; \
  fi; \
  exit $GRADLE_EXIT_CODE"]

USER 185
