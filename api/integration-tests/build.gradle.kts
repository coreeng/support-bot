plugins {
    id("java")
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("io.fabric8:kubernetes-client:6.10.0")
    testImplementation("io.rest-assured:rest-assured:5.5.+")

    // Avoiding vulnerability reports from rest-assured
    testImplementation("org.apache.commons:commons-lang3:3.18.+")
    testImplementation("commons-codec:commons-codec:1.18.+")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.0")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.25.0")
    testRuntimeOnly("org.junit.platform:junit-platform-console")
    
    testImplementation("org.awaitility:awaitility:4.3.+")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

application {
    mainClass.set("org.junit.platform.console.ConsoleLauncher")
}

tasks.jar {
	// Ensure all artifacts on the test runtime classpath are built before we
	// assemble the fat jar (e.g. any future project dependencies).
	dependsOn(configurations.testRuntimeClasspath.get().buildDependencies)

	from(sourceSets.test.get().output)
	from(configurations.testRuntimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
	manifest {
		attributes["Main-Class"] = "org.junit.platform.console.ConsoleLauncher"
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}