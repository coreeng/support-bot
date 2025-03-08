## Build stage
FROM gradle:jdk21 AS build

RUN apt-get update && apt-get install -y postgresql postgresql-contrib

WORKDIR /build

RUN sed -i 's/peer/trust/' '/etc/postgresql/16/main/pg_hba.conf' && \
    sed -i 's/md5/trust/' '/etc/postgresql/16/main/pg_hba.conf'

COPY . /build

RUN service postgresql start && sleep 6 && \
  su postgres -c "psql -c \"CREATE DATABASE flywaydb;\"" && \
  su postgres -c "psql -c \"ALTER USER postgres WITH PASSWORD 'fly';\"" && \
  su postgres -c "psql -c \"GRANT ALL PRIVILEGES ON DATABASE flywaydb TO postgres;\"" && \
  gradle -Ddocker=true :service:jooqCodegen :service:build --no-daemon

CMD ["gradle"]

## Final stage
FROM registry.access.redhat.com/ubi8/openjdk-21:1.21

WORKDIR /app
COPY --from=build /build/service/build/libs/service-*-SNAPSHOT.jar /app/service.jar

CMD ["java", "-jar", "/app/service.jar"]

EXPOSE 8080 8081
