rootProject.name = "support-bot-api"

// Only include modules if their directories exist (supports Docker partial builds)
listOf("service", "testkit", "functional", "nft", "integration-tests").forEach { module ->
    if (file(module).isDirectory) {
        include(module)
    }
}
