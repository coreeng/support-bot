import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    pmd
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.coreeng"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

pmd {
    isIgnoreFailures = false
    isConsoleOutput = true
    ruleSetFiles("$rootDir/pmd-ruleset.xml")
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
        // we need it just for the actuator, so pick Jetty instead of Tomcat since it's a bit more lightweight
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.3")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

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
    val imageTag = System.getProperty("imageTag") ?: "latest"
    imageName = "ghcr.io/coreeng/support-bot:${imageTag}"

    docker {
        publishRegistry {
            username = System.getProperty("username")
            password = System.getProperty("password")
        }
    }
    setPullPolicy("IF_NOT_PRESENT")
}
