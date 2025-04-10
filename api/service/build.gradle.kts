import org.flywaydb.gradle.task.AbstractFlywayTask
import org.jooq.codegen.gradle.CodegenTask
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    java
    pmd

    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"

    id("org.flywaydb.flyway") version "11.3.0"
    id("org.jooq.jooq-codegen-gradle") version "3.19.18"
}

group = "com.coreeng"
version = project.findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

pmd {
    isIgnoreFailures = false
    isConsoleOutput = true
    ruleSetFiles("${project.projectDir.absolutePath}/pmd-ruleset.xml")
    toolVersion = "7.9.0"
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val lombokVersion = "1.18.36"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.3")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jooq:jooq:3.19.18")
    implementation("org.jooq:jooq-meta:3.19.18")
    implementation("org.jooq:jooq-codegen:3.19.18")

    jooqCodegen("org.postgresql:postgresql:42.7.5")
    jooqCodegen("org.testcontainers:postgresql:1.20.4")
    jooqCodegen("org.jooq:jooq-codegen:3.19.18")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework:spring-context-indexer")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava")
    implementation("com.slack.api:bolt-jakarta-socket-mode:1.45.0")
    compileOnly("jakarta.websocket:jakarta.websocket-client-api:2.2.0")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.2.0")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("io.fabric8:kubernetes-client:7.0.1")

    implementation("com.google.cloud:spring-cloud-gcp-starter:5.10.0")
    implementation("com.google.apis:google-api-services-cloudidentity:v1-rev20241208-2.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    testCompileOnly("org.projectlombok:lombok:${lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombokVersion}")
}

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}
tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "-javaagent:${mockitoAgent.asPath}",
        "-XX:+EnableDynamicAgentLoading", "-Xshare:off"
    )
}

tasks.withType<BootBuildImage> {
    imageName = System.getProperty("imageName") ?: "support-bot:latest"
    docker {
        publishRegistry {
            username = System.getProperty("username")
            password = System.getProperty("password")
        }
    }
    setPullPolicy("IF_NOT_PRESENT")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.3.0")
        classpath("org.postgresql:postgresql:42.7.5")
        classpath("org.testcontainers:postgresql:1.20.4")
        classpath("org.jooq:jooq-codegen:3.19.18")
    }
}

val postgresService =
    project.gradle.sharedServices.registerIfAbsent("postgresContainer", PostgresService::class.java) {}

flyway {
    locations = arrayOf("filesystem:./src/main/resources/db/migration")
}
tasks.withType<AbstractFlywayTask> {
    val dockerBuild = System.getProperty("docker") ?: "false"
    if (dockerBuild != "true") {
        usesService(postgresService)
    }
    inputs.dir("src/main/resources/db/migration")
    doFirst {
        val container = if (dockerBuild != "true") {
            postgresService.get().container
        } else null
        url = container?.jdbcUrl ?: "jdbc:postgresql://localhost:5432/flywaydb"
        user = container?.username ?: "postgres"
        password = container?.password ?: "fly"
    }
}

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = ".*"
                excludes = "flyway_schema_history"
                inputSchema = "public"

                forcedTypes {
                    forcedType {
                        includeTypes = "timestamptz"
                        userType = Instant::class.java.canonicalName
                    }
                }
            }
            target {
                packageName = "com.coreeng.supportbot.dbschema"
            }
        }
    }
}
tasks.withType<CodegenTask> {
    val dockerBuild = System.getProperty("docker") ?: "false"
    if (dockerBuild != "true") {
        usesService(postgresService)
    }
    dependsOn("flywayMigrate")
    doFirst {
        val container = if (dockerBuild != "true") {
            postgresService.get().container
        } else null
        jooq {
            configuration {
                jdbc {
                    driver = "org.postgresql.Driver"
                    url = container?.jdbcUrl ?: "jdbc:postgresql://localhost:5432/flywaydb"
                    user = container?.username ?: "postgres"
                    password = container?.password ?: "fly"
                }
            }
        }
    }
}

abstract class PostgresService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    val container = PostgreSQLContainer("postgres:17.2-alpine").apply {
        withDatabaseName("postgres")
        withUsername("postgres")
        withPassword("postgres")
        setWaitStrategy(
            WaitAllStrategy()
                .withStrategy(
                    LogMessageWaitStrategy()
                        .withRegEx(".*database system is ready to accept connections.*\\s")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
                )
                .withStrategy(Wait.forListeningPort())
        )
        start()
    }

    override fun close() {
        container.stop()
    }
}
