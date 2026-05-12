## Build stage
FROM docker.io/gradle:9.5.0-jdk25-noble AS build
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
COPY config config
COPY build.gradle.kts .

ARG P2P_VERSION

# hadolint ignore=DL3001
RUN service postgresql start && sleep 6 && \
    su postgres -c "psql -c \"CREATE DATABASE flywaydb;\"" && \
    su postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD 'fly';\"" && \
    su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE flywaydb TO postgres;\"" && \
    ./gradlew -Ddocker=true \
        -Dsupportbot.localDb.url=jdbc:postgresql://localhost:5432/flywaydb \
        -Dsupportbot.localDb.user=postgres \
        -Dsupportbot.localDb.password=fly \
        :service:jooqCodegen :service:build -Pversion="${P2P_VERSION}" --no-daemon

## Final stage
FROM docker.io/eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /build/service/build/libs/service.jar /app/service.jar
COPY service/analysis analysis

CMD ["java", "-jar", "/app/service.jar"]

EXPOSE 8080 8081
USER 185
