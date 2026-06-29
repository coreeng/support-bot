plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

val jacksonVersion = "2.22.0"
val jacksonBom = "com.fasterxml.jackson:jackson-bom:$jacksonVersion"
val safeDependencyVersions =
    mapOf(
        "ch.qos.logback:logback-core" to "1.5.25",
        "com.github.jknack:handlebars" to "4.5.2",
        "com.microsoft.kiota:microsoft-kiota-abstractions" to "1.9.1",
        "com.nimbusds:nimbus-jose-jwt" to "10.0.2",
        "com.squareup.okhttp3:okhttp" to "4.12.0",
        "com.squareup.okio:okio" to "3.16.4",
        "io.netty:netty-codec" to "4.1.135.Final",
        "io.netty:netty-codec-dns" to "4.1.135.Final",
        "io.netty:netty-codec-http" to "4.1.135.Final",
        "io.netty:netty-codec-http2" to "4.1.135.Final",
        "io.netty:netty-common" to "4.1.135.Final",
        "io.netty:netty-handler" to "4.1.135.Final",
        "io.netty:netty-handler-proxy" to "4.1.135.Final",
        "io.netty:netty-resolver-dns" to "4.1.135.Final",
        "io.netty:netty-transport-native-epoll" to "4.1.135.Final",
        "io.netty:netty-transport-native-kqueue" to "4.1.135.Final",
        "io.opentelemetry:opentelemetry-api" to "1.62.0",
        "io.vertx:vertx-core" to "4.5.27",
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
        "org.springframework:spring-context" to "6.2.18",
        "org.springframework:spring-core" to "6.2.18",
        "org.springframework:spring-web" to "6.2.18",
        "org.springframework:spring-webmvc" to "6.2.18",
    )
val managedDependencyVersions =
    mapOf(
        "asm.version" to "9.9.1",
        "byte-buddy.version" to "1.18.4",
        "commons-lang3.version" to safeDependencyVersions.getValue("org.apache.commons:commons-lang3"),
        "mockito.version" to "5.21.0",
        "netty.version" to safeDependencyVersions.getValue("io.netty:netty-common"),
        "opentelemetry.version" to safeDependencyVersions.getValue("io.opentelemetry:opentelemetry-api"),
        "postgresql.version" to safeDependencyVersions.getValue("org.postgresql:postgresql"),
        "spring-framework.version" to safeDependencyVersions.getValue("org.springframework:spring-core"),
    )

subprojects {
    apply(plugin = "com.diffplug.spotless")

    extra["jackson-bom.version"] = jacksonVersion
    managedDependencyVersions.forEach { (property, version) ->
        extra[property] = version
    }

    plugins.withType<JavaPlugin> {
        dependencies {
            add("implementation", enforcedPlatform(jacksonBom))
            add("testImplementation", enforcedPlatform(jacksonBom))
            configurations.findByName("api")?.let {
                add("api", enforcedPlatform(jacksonBom))
            }
        }
    }

    configurations.configureEach {
        if (isCanBeDeclared) {
            safeDependencyVersions.forEach { (dependency, safeVersion) ->
                project.dependencies.constraints.add(name, dependency) {
                    version {
                        strictly(safeVersion)
                    }
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
