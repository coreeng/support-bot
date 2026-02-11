plugins {
    id("com.diffplug.spotless") version "8.0.0" apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

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
