import org.flywaydb.gradle.task.AbstractFlywayTask
import org.jooq.codegen.gradle.CodegenTask
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.gradle.api.Action
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

plugins {
    java
    checkstyle

    id("net.ltgt.errorprone") version "4.3.0"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"

    id("org.flywaydb.flyway") version "12.0.0"
    id("org.jooq.jooq-codegen-gradle") version "3.19.18"
}

group = "com.coreeng"
version = project.findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

checkstyle {
    toolVersion = "10.25.0"
    isIgnoreFailures = false
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}
// we only use result of the bootJar
tasks.getByName<Jar>("jar") {
    enabled = false
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("service.jar")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

// Java 25 compatibility: override transitive dependency versions
extra["byte-buddy.version"] = "1.18.4"
extra["mockito.version"] = "5.21.0"
extra["asm.version"] = "9.9.1"

val lombokVersion = "1.18.42"
val errorProneVersion = "2.47.0"
val nullAwayVersion = "0.13.1"

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")

    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security + OAuth2 + JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jooq:jooq:3.19.18")
    implementation("org.jooq:jooq-meta:3.19.18")
    implementation("org.jooq:jooq-codegen:3.19.18")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.microsoft.kiota:microsoft-kiota-http-okHttp:1.8.5")

    jooqCodegen("org.postgresql:postgresql:42.7.5")
    jooqCodegen("org.testcontainers:postgresql:1.20.4")
    jooqCodegen("org.jooq:jooq-codegen:3.19.18")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework:spring-context-indexer")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava")
    implementation("com.slack.api:bolt-jakarta-socket-mode:1.45.4")
    implementation("com.slack.api:bolt-jakarta-servlet:1.45.4")
    compileOnly("jakarta.websocket:jakarta.websocket-client-api:2.2.0")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.2.0")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("io.fabric8:kubernetes-client:7.1.0")
    testImplementation("io.fabric8:kubernetes-server-mock:7.1.0")

    implementation("dev.cel:cel:0.11.1")

    implementation("com.google.cloud:spring-cloud-gcp-starter:5.10.0")
    implementation("com.google.apis:google-api-services-cloudidentity:v1-rev20241208-2.0.0")

    implementation("com.microsoft.graph:microsoft-graph:6.36.0")
    implementation("com.azure.spring:spring-cloud-azure-starter:5.22.0")

    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")
    testCompileOnly("org.projectlombok:lombok:${lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    errorprone("com.google.errorprone:error_prone_core:${errorProneVersion}")
    errorprone("com.uber.nullaway:nullaway:${nullAwayVersion}")
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

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
    options.errorprone(
        object : Action<ErrorProneOptions> {
            override fun execute(errorproneOptions: ErrorProneOptions) {
                // Disabled checks (per project standards)
                errorproneOptions.disable(
                    "UnusedVariable",
                    "UnusedMethod",
                    "FieldCanBeStatic",
                    "ImmutableEnumChecker",
                    "MissingSummary",
                    "UnusedNestedClass",
                    // UnnecessarilyFullyQualified false-positives on Lombok's @Builder.Default
                    "UnnecessarilyFullyQualified",
                    // CanIgnoreReturnValueSuggester false-positives on repository update patterns
                    "CanIgnoreReturnValueSuggester"
                )

                // Elevated to ERROR
                errorproneOptions.error(
                    "UnusedException",
                    "FutureReturnValueIgnored",
                    "StreamResourceLeak"
                )

                // NullAway configuration
                errorproneOptions.check("NullAway", CheckSeverity.ERROR)
                errorproneOptions.option("NullAway:AnnotatedPackages", "com.coreeng.supportbot")
                errorproneOptions.option(
                    "NullAway:UnannotatedSubPackages",
                    "com.coreeng.supportbot.dbschema"
                )
                errorproneOptions.option("NullAway:JSpecifyMode", true)
                errorproneOptions.option("NullAway:HandleTestAssertionLibraries", true)
                errorproneOptions.option(
                    "NullAway:ExcludedFieldAnnotations",
                    "org.mockito.Mock,org.mockito.Spy,org.mockito.Captor,org.mockito.InjectMocks"
                )

                // Exclude generated code
                errorproneOptions.excludedPaths.set(".*/com/coreeng/supportbot/dbschema/.*")
            }
        }
    )
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.0.0")
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
                    forcedType {
                        userType = "java.util.List<java.lang.String>"
                        isAutoConverter = true
                        includeTypes = "_varchar"
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

tasks.withType<JavaCompile> {
    mustRunAfter("jooqCodegen")
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
