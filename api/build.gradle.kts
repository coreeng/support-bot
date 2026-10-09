plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

val jackson2Version = "2.22.3"
val jacksonVersion = "3.2.3"
val vertxVersion = "5.2.0"
// SDKs and WireMock still use Jackson 2; application and testkit JSON use Jackson 3.
val jackson2Bom = "com.fasterxml.jackson:jackson-bom:$jackson2Version"
// Preserve the patched transport floors as complete families rather than mixed-generation artifact pins.
val jettyVersion = "12.1.12"
val nettyVersion = "4.2.17.Final"

// gRPC artifacts must move as a set. Pinning grpc-netty-shaded alone for a security alert (see
// safeDependencyVersions below) left it ahead of grpc-core/grpc-api, which google-cloud-vertexai
// then brought in at 1.71.0 — and the newer shaded Netty reads GrpcAttributes.ATTR_AUTHORITY_VERIFIER,
// a field the older grpc-core does not have. Every Vertex call then died with a NoSuchFieldError at
// TLS negotiation. The BOM keeps the whole family on one version so a future single-artifact bump
// cannot split them again.
val grpcVersion = "1.83.1"
val grpcBom = "io.grpc:grpc-bom:$grpcVersion"
val safeDependencyVersions =
    mapOf(
        "ch.qos.logback:logback-core" to "1.5.38",
        "com.github.jknack:handlebars" to "4.5.2",
        "com.squareup.okio:okio" to "3.16.4",
        // Kept so the security requirement stays visible, but sourced from grpcVersion: the BOM above
        // is what actually aligns the family, and a literal here could silently drift from it.
        "io.grpc:grpc-netty-shaded" to grpcVersion,
        "io.opentelemetry:opentelemetry-api" to "1.62.0",
        "net.minidev:json-smart" to "2.5.2",
        "net.sourceforge.pmd:pmd-core" to "7.22.0",
        "org.apache.commons:commons-compress" to "1.26.0",
        "org.apache.commons:commons-lang3" to "3.18.0",
        "org.apache.httpcomponents.client5:httpclient5" to "5.4.3",
        "org.apache.logging.log4j:log4j-api" to "2.25.5",
        "org.apache.logging.log4j:log4j-core" to "2.25.5",
        "org.bouncycastle:bcpkix-jdk18on" to "1.86",
        "org.bouncycastle:bcprov-jdk18on" to "1.86",
        "org.bouncycastle:bcutil-jdk18on" to "1.86",
        "org.codehaus.plexus:plexus-utils" to "4.0.3",
        "org.postgresql:postgresql" to "42.7.12",
    )
val managedDependencyVersions =
    mapOf(
        "asm.version" to "9.9.1",
        "byte-buddy.version" to "1.18.4",
        "commons-lang3.version" to safeDependencyVersions.getValue("org.apache.commons:commons-lang3"),
        "jetty.version" to jettyVersion,
        "netty.version" to nettyVersion,
        "logback.version" to safeDependencyVersions.getValue("ch.qos.logback:logback-core"),
        "log4j2.version" to safeDependencyVersions.getValue("org.apache.logging.log4j:log4j-api"),
        "mockito.version" to "5.21.0",
        "opentelemetry.version" to safeDependencyVersions.getValue("io.opentelemetry:opentelemetry-api"),
        "postgresql.version" to safeDependencyVersions.getValue("org.postgresql:postgresql"),
    )

subprojects {
    apply(plugin = "com.diffplug.spotless")

    extra["jackson-2-bom.version"] = jackson2Version
    extra["jackson-bom.version"] = jacksonVersion
    extra["vertx.version"] = vertxVersion
    extra["jooq.version"] = "3.21.9"
    managedDependencyVersions.forEach { (property, version) ->
        extra[property] = version
    }

    plugins.withType<JavaPlugin> {
        dependencies {
            add("implementation", enforcedPlatform(jackson2Bom))
            add("testImplementation", enforcedPlatform(jackson2Bom))
            add("implementation", enforcedPlatform(grpcBom))
            add("testImplementation", enforcedPlatform(grpcBom))
            listOf(
                "org.eclipse.jetty:jetty-bom:$jettyVersion",
                "org.eclipse.jetty.ee10:jetty-ee10-bom:$jettyVersion",
                "io.netty:netty-bom:$nettyVersion",
            ).forEach { bom ->
                add("implementation", enforcedPlatform(bom))
                add("testImplementation", enforcedPlatform(bom))
                configurations.findByName("api")?.let { add("api", enforcedPlatform(bom)) }
            }
            configurations.findByName("api")?.let {
                add("api", enforcedPlatform(jackson2Bom))
                add("api", enforcedPlatform(grpcBom))
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
