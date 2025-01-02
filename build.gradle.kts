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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework:spring-context-indexer")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.slack.api:bolt-jakarta-socket-mode:1.44.2")
    compileOnly("jakarta.websocket:jakarta.websocket-client-api:2.2.0")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.2.0")
    implementation("com.google.guava:guava:33.4.0-jre")

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
