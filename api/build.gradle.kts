plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

val safeDependencyVersions =
    mapOf(
        "ch.qos.logback:logback-core" to "1.5.25",
        "com.fasterxml.jackson.core:jackson-core" to "2.18.6",
        "io.netty:netty-codec" to "4.1.133.Final",
        "io.netty:netty-codec-dns" to "4.1.133.Final",
        "io.netty:netty-codec-http" to "4.1.133.Final",
        "io.netty:netty-codec-http2" to "4.1.133.Final",
        "io.netty:netty-common" to "4.1.133.Final",
        "io.netty:netty-handler" to "4.1.133.Final",
        "io.netty:netty-handler-proxy" to "4.1.133.Final",
        "io.vertx:vertx-core" to "4.5.24",
        "net.minidev:json-smart" to "2.5.2",
        "net.sourceforge.pmd:pmd-core" to "7.22.0",
        "org.apache.commons:commons-compress" to "1.26.0",
        "org.apache.httpcomponents.client5:httpclient5" to "5.4.3",
        "org.assertj:assertj-core" to "3.27.7",
        "org.eclipse.jetty:jetty-http" to "12.0.35",
        "org.eclipse.jetty:jetty-server" to "12.0.35",
        "org.postgresql:postgresql" to "42.7.11",
        "org.springframework:spring-context" to "6.2.18",
        "org.springframework:spring-core" to "6.2.18",
        "org.springframework:spring-web" to "6.2.18",
        "org.springframework:spring-webmvc" to "6.2.18",
    )
val safeDependencyCoordinates = safeDependencyVersions.map { (dependency, version) -> "$dependency:$version" }.toTypedArray()

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configurations.configureEach {
        resolutionStrategy {
            force(*safeDependencyCoordinates)
            eachDependency {
                safeDependencyVersions["${requested.group}:${requested.name}"]?.let { safeVersion ->
                    useVersion(safeVersion)
                    because("Patched version required for Dependabot security alerts")
                }
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            targetExclude("**/dbschema/**")
            importOrder()
            removeUnusedImports()
            palantirJavaFormat("2.86.0")
            formatAnnotations()
        }
        format("gradle") {
            target("*.gradle", "*.gradle.kts")
            trimTrailingWhitespace()
            leadingTabsToSpaces()
            endWithNewline()
        }
    }
}
