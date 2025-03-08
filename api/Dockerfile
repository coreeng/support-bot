## Build stage
FROM gradle:jdk21 AS build
WORKDIR /build

# hadolint ignore=DL3008
RUN apt-get update && apt-get install -y --no-install-recommends postgresql postgresql-contrib

RUN sed -i 's/peer/trust/' '/etc/postgresql/16/main/pg_hba.conf' && \
    sed -i 's/md5/trust/' '/etc/postgresql/16/main/pg_hba.conf'

COPY settings.gradle.kts .
COPY service service

ARG P2P_VERSION

# hadolint ignore=DL3001
RUN service postgresql start && sleep 6 && \
    su postgres -c "psql -c \"CREATE DATABASE flywaydb;\"" && \
    su postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD 'fly';\"" && \
    su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE flywaydb TO postgres;\"" && \
    gradle -Ddocker=true :service:jooqCodegen :service:build -Pversion="${P2P_VERSION}" --no-daemon

## Final stage
FROM registry.access.redhat.com/ubi8/openjdk-21:1.21
WORKDIR /
COPY --from=build /build/service/build/libs/service-*.jar /service.jar

CMD ["java", "-jar", "service.jar"]

EXPOSE 8080 8081
