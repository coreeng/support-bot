package com.coreeng.supportbot.teams.groups;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public sealed interface GroupRef
        permits GroupRef.Slack, GroupRef.Google, GroupRef.Azure, GroupRef.Jwt, GroupRef.Static {

    Logger LOGGER = LoggerFactory.getLogger(GroupRef.class);

    Pattern PREFIXED = Pattern.compile("^([a-z]+):(.+)$");
    Pattern LEGACY_SLACK_PATTERN = Pattern.compile("^S[A-Z0-9]{8,}$");

    String value();

    Provider provider();

    default String canonical() {
        return provider().prefix() + ":" + value();
    }

    record Slack(String id) implements GroupRef {
        public Slack {
            requireNonBlank(id, "Slack");
        }

        @Override
        public String value() {
            return id;
        }

        @Override
        public Provider provider() {
            return Provider.SLACK;
        }

        /** Extracts the Slack subteam id from {@code ref}, or throws if {@code ref} isn't Slack-typed. */
        public static String idFrom(GroupRef ref, String configKey) {
            if (ref instanceof Slack slack) {
                return slack.id();
            }
            throw new IllegalStateException(configKey + " must reference a Slack group; got " + ref.canonical());
        }
    }

    record Google(String key) implements GroupRef {
        public Google {
            requireNonBlank(key, "Google");
        }

        @Override
        public String value() {
            return key;
        }

        @Override
        public Provider provider() {
            return Provider.GOOGLE;
        }
    }

    record Azure(String objectId) implements GroupRef {
        public Azure {
            requireNonBlank(objectId, "Azure");
        }

        @Override
        public String value() {
            return objectId;
        }

        @Override
        public Provider provider() {
            return Provider.AZURE;
        }
    }

    record Jwt(String groupName) implements GroupRef {
        public Jwt {
            requireNonBlank(groupName, "Jwt");
        }

        @Override
        public String value() {
            return groupName;
        }

        @Override
        public Provider provider() {
            return Provider.JWT;
        }
    }

    record Static(String key) implements GroupRef {
        public Static {
            requireNonBlank(key, "Static");
        }

        @Override
        public String value() {
            return key;
        }

        @Override
        public Provider provider() {
            return Provider.STATIC;
        }
    }

    static GroupRef parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw new GroupRefParseException(
                    "GroupRef cannot be blank (got: " + (raw == null ? "null" : "\"" + raw + "\"") + ")");
        }
        Matcher m = PREFIXED.matcher(raw);
        if (m.matches()) {
            return parsePrefixed(m.group(1), m.group(2), raw);
        }
        return parseLegacyBareString(raw);
    }

    private static GroupRef parsePrefixed(String prefix, String value, String raw) {
        return Provider.fromPrefix(prefix)
                .map(p -> p.build(value))
                .orElseThrow(() -> new GroupRefParseException(
                        "Unknown GroupRef provider prefix \"" + prefix + "\" in \"" + raw + "\""));
    }

    /**
     * Legacy parsing path: infers the provider from the shape of an unprefixed string.
     * <p>Exists only for backwards compatibility with pre-PT-351 YAML; new configurations
     * should use the typed {@code "<provider>:<value>"} form. Bare-string parsing will be
     * removed in the release following PT-351.
     */
    private static GroupRef parseLegacyBareString(String raw) {
        GroupRef inferred;
        if (LEGACY_SLACK_PATTERN.matcher(raw).matches()) {
            inferred = new Slack(raw);
        } else if (isUuid(raw)) {
            inferred = new Azure(raw);
        } else if (raw.contains("@")) {
            inferred = new Google(raw);
        } else {
            inferred = new Static(raw);
        }
        LOGGER.warn(
                "Parsed legacy bare-string GroupRef \"{}\" as {} via shape inference — "
                        + "migrate to typed form \"{}\". Bare-string parsing will be removed in the release following PT-351.",
                raw,
                inferred.provider(),
                inferred.canonical());
        return inferred;
    }

    private static boolean isUuid(String raw) {
        try {
            UUID.fromString(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void requireNonBlank(@Nullable String s, String type) {
        if (s == null || s.isBlank()) {
            throw new GroupRefParseException("GroupRef." + type + " value cannot be blank");
        }
    }

    enum Provider {
        SLACK("slack", Slack::new),
        GOOGLE("google", Google::new),
        AZURE("azure", Azure::new),
        JWT("jwt", Jwt::new),
        STATIC("static", Static::new);

        private final String prefix;
        private final Function<String, GroupRef> factory;

        Provider(String prefix, Function<String, GroupRef> factory) {
            this.prefix = prefix;
            this.factory = factory;
        }

        public String prefix() {
            return prefix;
        }

        GroupRef build(String value) {
            return factory.apply(value);
        }

        static Optional<Provider> fromPrefix(String prefix) {
            for (Provider p : values()) {
                if (p.prefix.equals(prefix)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }
    }
}
