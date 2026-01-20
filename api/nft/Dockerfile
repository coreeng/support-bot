FROM gradle:8.12.1-jdk21 AS builder
WORKDIR /home/gradle/project

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Copy Gradle wrapper and core build configuration
COPY gradlew .
COPY settings.gradle.kts .
COPY gradle gradle
COPY buildSrc buildSrc

# Warm up Gradle wrapper and download plugins/dependencies metadata
RUN ./gradlew --no-daemon help

# Copy only the subprojects needed for NFTs to keep build cache efficient
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

# --- Runtime image ---
# Use full JDK (not just the runtime) so Gradle/buildSrc have a Java compiler
# available when running :nft:gatlingRun inside the container. Pin the image
# version to avoid surprises from implicit base image updates.
FROM registry.access.redhat.com/ubi8/openjdk-21:1.23
WORKDIR /app

# Copy the pre-built project tree and Gradle user home from the builder stage.
# At runtime, we copy the project into /tmp (the only writable volume when
# readOnlyRootFilesystem=true) before running Gradle.
COPY --from=builder /home/gradle/project /opt/project
COPY --from=builder /home/gradle/.gradle /opt/gradle-home

# Gradle caches will live under /tmp/gradle-home. We selectively copy the most
# useful parts of the builder's Gradle user home (wrapper + dependency caches)
# into this directory at container startup to avoid expensive re-downloads,
# but we intentionally skip lock files and daemon state to prevent lock errors.
ENV GRADLE_USER_HOME=/tmp/gradle-home

# Fully-qualified name of the NFT simulation to run
ENV NFT_SIMULATION_FQCN="com.coreeng.supportbot.nft.TicketFlowSimulation"

# JAVA_OPTS is set from the Helm chart for this Job and already includes:
#   -Xms / -Xmx
#   -Djava.util.prefs.userRoot=/tmp/java-prefs
# The Gatling Gradle plugin also adds the necessary --add-opens flags for JDK 21.

# ENTRYPOINT flow (kept simple):
#   1. Copy the pre-built project into /tmp/project (writable under the K8s
#      emptyDir volume) so Gradle and Gatling can write build and report output.
#   2. Copy the Gradle user home from the builder stage into /tmp/gradle-home
#      so runtime Gradle can reuse dependency caches while still writing only
#      under /tmp.
#   3. Run the Gatling Gradle task in non-interactive, offline mode for the NFT
#      simulation.
ENTRYPOINT ["sh", "-c", "\
  set -e; \
  mkdir -p /tmp/project \"$GRADLE_USER_HOME\"; \
  cp -R /opt/project/. /tmp/project/; \
  if [ -d /opt/gradle-home ]; then \
    cp -R /opt/gradle-home/. \"$GRADLE_USER_HOME/\"; \
  fi; \
  cd /tmp/project && \
  CI=true ./gradlew :nft:gatlingRun --no-daemon --offline --simulation=$NFT_SIMULATION_FQCN"]

USER 185
