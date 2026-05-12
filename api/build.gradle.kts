plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

val safeDependencyVersions =
    mapOf(
        "ch.qos.logback:logback-core" to "1.5.25",
        "com.fasterxml.jackson.core:jackson-core" to "2.21.2",
        "com.microsoft.kiota:microsoft-kiota-abstractions" to "1.9.1",
        "com.squareup.okhttp3:okhttp" to "4.12.0",
        "com.squareup.okio:okio" to "3.16.4",
        "io.grpc:grpc-netty-shaded" to "1.75.0",
        "io.netty:netty-codec" to "4.1.133.Final",
        "io.netty:netty-codec-dns" to "4.1.133.Final",
        "io.netty:netty-codec-http" to "4.1.133.Final",
        "io.netty:netty-codec-http2" to "4.1.133.Final",
        "io.netty:netty-common" to "4.1.133.Final",
        "io.netty:netty-handler" to "4.1.133.Final",
        "io.netty:netty-handler-proxy" to "4.1.133.Final",
        "io.netty:netty-transport-native-epoll" to "4.2.13.Final",
        "io.vertx:vertx-core" to "4.5.24",
        "io.vertx:vertx-web" to "4.5.22",
        "net.minidev:json-smart" to "2.5.2",
        "net.sourceforge.pmd:pmd-core" to "7.22.0",
        "org.apache.commons:commons-compress" to "1.26.0",
        "org.apache.commons:commons-lang3" to "3.18.0",
        "org.apache.httpcomponents.client5:httpclient5" to "5.4.3",
        "org.apache.logging.log4j:log4j-core" to "2.25.4",
        "org.apache.opennlp:opennlp-tools" to "2.5.9",
        "org.assertj:assertj-core" to "3.27.7",
        "org.bouncycastle:bcpkix-jdk18on" to "1.84",
        "org.bouncycastle:bcprov-jdk18on" to "1.84",
        "org.codehaus.plexus:plexus-utils" to "4.0.3",
        "org.eclipse.jetty:jetty-http" to "12.0.35",
        "org.eclipse.jetty:jetty-server" to "12.0.35",
        "org.postgresql:postgresql" to "42.7.11",
        "org.springframework:spring-context" to "7.0.6",
        "org.springframework:spring-core" to "7.0.6",
        "org.springframework:spring-web" to "7.0.6",
        "org.springframework:spring-webmvc" to "7.0.6",
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
