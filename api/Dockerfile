## Build stage
FROM gradle:8.12.1-jdk21 AS build
WORKDIR /build

# hadolint ignore=DL3008
RUN apt-get update && apt-get install -y --no-install-recommends postgresql postgresql-contrib && \
    rm -rf /var/lib/apt/lists/*

RUN sed -i 's/peer/trust/' '/etc/postgresql/16/main/pg_hba.conf' && \
    sed -i 's/md5/trust/' '/etc/postgresql/16/main/pg_hba.conf'

COPY gradlew .
COPY settings.gradle.kts .
COPY gradle gradle
RUN ./gradlew --no-daemon

COPY service service

ARG P2P_VERSION

# hadolint ignore=DL3001
RUN service postgresql start && sleep 6 && \
    su postgres -c "psql -c \"CREATE DATABASE flywaydb;\"" && \
    su postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD 'fly';\"" && \
    su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE flywaydb TO postgres;\"" && \
    ./gradlew -Ddocker=true :service:jooqCodegen :service:build -Pversion="${P2P_VERSION}" --no-daemon

## Final stage
FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:1.23
WORKDIR /app
COPY --from=build /build/service/build/libs/service-*.jar /app/service.jar

CMD ["java", "-jar", "/app/service.jar"]

EXPOSE 8080 8081
USER 1001
