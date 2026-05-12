plugins {
    `java-library`
    checkstyle
}

repositories {
    mavenCentral()
}

checkstyle {
    toolVersion = "10.25.0"
    isIgnoreFailures = false
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val lombokVersion = "1.18.46"

dependencies {
    // Logging
    api("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")

    // Lombok
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    // Commons
    api("org.apache.commons:commons-text:1.13.1")
    api("org.apache.commons:commons-lang3:3.18.0")
    api("commons-codec:commons-codec:1.19.0")

    // JUnit platform (for LauncherSessionListener)
    api(platform("org.junit:junit-bom:6.0.3"))
    api("org.junit.jupiter:junit-jupiter:6.0.3")
    api("org.junit.platform:junit-platform-launcher:6.0.3")

    // Testing utilities
    api("org.awaitility:awaitility:4.3.0")
    api("org.assertj:assertj-core:3.27.7")

    // WireMock
    api("org.wiremock:wiremock:3.13.2")
    // Override Jetty to address security findings reported for wiremock's transitive dependency
    implementation("org.eclipse.jetty:jetty-server:11.0.26")

    // Jackson
    api("com.fasterxml.jackson.core:jackson-databind:2.18.6")
    api("com.fasterxml.jackson.datatype:jackson-datatype-guava:2.18.6")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.6")

    // RestAssured
    api("io.rest-assured:rest-assured:6.0.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // JSON unit for assertions
    api("net.javacrumbs.json-unit:json-unit-assertj:4.1.1")

    // DataFaker for test data generation
    api("net.datafaker:datafaker:2.4.3")

    // Spring Boot for config loading
    implementation("org.springframework.boot:spring-boot:4.0.6")

    // JSpecify for null annotations
    api("org.jspecify:jspecify:1.0.0")

    // Guava
    api("com.google.guava:guava:33.6.0-jre")
}
