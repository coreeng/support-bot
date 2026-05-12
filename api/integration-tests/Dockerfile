FROM docker.io/gradle:9.5.0-jdk25-noble AS builder
WORKDIR /home/gradle/project

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
# Install Helm and kubectl with pinned versions and checksum verification
ARG HELM_VERSION=3.19.0
ARG KUBECTL_VERSION=v1.33.5

RUN set -euxo pipefail; \
  curl -fsSLO "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" && \
  curl -fsSLO "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz.sha256sum" && \
  sha256sum -c "helm-v${HELM_VERSION}-linux-amd64.tar.gz.sha256sum" && \
  tar -xzf "helm-v${HELM_VERSION}-linux-amd64.tar.gz" && \
  mv linux-amd64/helm /usr/local/bin/helm && \
  rm -rf linux-amd64 "helm-v${HELM_VERSION}-linux-amd64.tar.gz"* && \
  curl -fsSLo kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" && \
  curl -fsSLo kubectl.sha256 "https://dl.k8s.io/${KUBECTL_VERSION}/bin/linux/amd64/kubectl.sha256" && \
  echo "$(cat kubectl.sha256)  kubectl" | sha256sum -c - && \
  install -m 0755 kubectl /usr/local/bin/kubectl && \
  rm -f kubectl kubectl.sha256

# Copy gradle wrapper and settings
COPY api/gradlew .
COPY api/settings.gradle.kts .
COPY api/gradle gradle

RUN ./gradlew --no-daemon

COPY api/integration-tests/build.gradle.kts integration-tests/build.gradle.kts
COPY api/integration-tests/src integration-tests/src

RUN ./gradlew :integration-tests:jar --no-daemon

FROM docker.io/eclipse-temurin:25-jre-noble
WORKDIR /app

COPY --from=builder /usr/local/bin/helm /usr/local/bin/helm
COPY --from=builder /usr/local/bin/kubectl /usr/local/bin/kubectl

COPY --from=builder /home/gradle/project/integration-tests/build/libs/integration-tests.jar .

COPY helm-chart helm-chart
COPY api/scripts scripts

ENV INTEGRATION_TEST_CONFIG=integration-test-docker.yaml

ENTRYPOINT ["java", "-jar", "integration-tests.jar", "execute", "-p", "com.coreeng.supportbot"]
USER 185
