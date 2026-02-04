rootProject.name = "support-bot-api"

include("service")

// Only include test modules if their directories exist (supports Docker partial builds)
listOf("testkit", "functional", "nft", "integration-tests").forEach { module ->
    if (file(module).isDirectory) {
        include(module)
    }
}
