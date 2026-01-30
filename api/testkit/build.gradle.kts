plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val lombokVersion = "1.18.+"

dependencies {
    // Logging
    api("org.slf4j:slf4j-api:2.0.+")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.+")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.+")

    // Lombok
    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    // Commons
    api("org.apache.commons:commons-text:1.13.1")
    api("org.apache.commons:commons-lang3:3.18.+")
    api("commons-codec:commons-codec:1.18.+")

    // JUnit platform (for LauncherSessionListener)
    api(platform("org.junit:junit-bom:5.13.+"))
    api("org.junit.jupiter:junit-jupiter:5.13.+")
    api("org.junit.platform:junit-platform-launcher:1.13.+")

    // Testing utilities
    api("org.awaitility:awaitility:4.3.+")
    api("org.assertj:assertj-core:3.27.+")

    // WireMock
    api("org.wiremock:wiremock:3.13.+")
    // Override Jetty to address security findings reported for wiremock's transitive dependency
    implementation("org.eclipse.jetty:jetty-server:11.0.+")

    // Jackson
    api("com.fasterxml.jackson.core:jackson-databind:2.18.+")
    api("com.fasterxml.jackson.datatype:jackson-datatype-guava:2.18.+")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.+")

    // RestAssured
    api("io.rest-assured:rest-assured:5.5.+") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // JSON unit for assertions
    api("net.javacrumbs.json-unit:json-unit-assertj:4.1.+")

    // DataFaker for test data generation
    api("net.datafaker:datafaker:2.4.3")

    // Spring Boot for config loading
    implementation("org.springframework.boot:spring-boot:3.5.6")

    // JSpecify for null annotations
    api("org.jspecify:jspecify:1.0.0")

    // Guava
    api("com.google.guava:guava:33.4.0-jre")
}

