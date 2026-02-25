package com.coreeng.supportbot.github;

import java.time.Instant;

public record GitHubInstallationToken(String token, Instant expiresAt) {}
