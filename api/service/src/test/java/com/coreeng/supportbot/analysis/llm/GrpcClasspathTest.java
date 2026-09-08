package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the gRPC artifacts against drifting apart on the classpath.
 *
 * <p>Written after every Vertex call started failing at TLS negotiation with {@code
 * NoSuchFieldError: ... GrpcAttributes ... ATTR_AUTHORITY_VERIFIER}. A security bump had pinned
 * {@code grpc-netty-shaded} on its own while {@code grpc-core}/{@code grpc-api} stayed on the older
 * version google-cloud-vertexai brings in; the newer shaded Netty reads a field the older core does
 * not declare. gRPC's artifacts are built and released as a set and are only compatible with each
 * other at the same version.
 *
 * <p>No test could have caught it by exercising the model — nothing in the suite calls a real
 * {@code ChatModel}, and a network-dependent test would be the wrong fix anyway. Comparing the
 * resolved jars costs nothing and fails the moment the set splits again.
 */
class GrpcClasspathTest {

    /** Matches the version in a Gradle-cached jar name, e.g. {@code grpc-core-1.75.0.jar}. */
    private static final Pattern JAR_VERSION =
            Pattern.compile("^(?<artifact>.+?)-(?<version>\\d+\\.\\d+\\.\\d+[^/]*)\\.jar$");

    @Test
    void everyGrpcArtifactResolvesToTheSameVersion() {
        Map<String, String> versions = new LinkedHashMap<>();
        // One class from each of the three artifacts that have to agree: the shaded transport, the
        // core it calls into (home of GrpcAttributes), and the API they share.
        versions.put("grpc-netty-shaded", versionOf(io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.class));
        versions.put("grpc-core", versionOf(io.grpc.internal.GrpcUtil.class));
        versions.put("grpc-api", versionOf(io.grpc.Attributes.class));

        assertThat(versions.values())
                .as(
                        "gRPC artifacts must all be on one version (resolved: %s) — a single-artifact bump "
                                + "splits the set and breaks Vertex at TLS negotiation with a NoSuchFieldError. "
                                + "Align them via the grpc-bom in the root build.gradle.kts.",
                        versions)
                .containsOnly(versions.get("grpc-api"));
    }

    private static String versionOf(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource)
                .as("No code source for %s — cannot tell which jar it came from", type.getName())
                .isNotNull();
        URL location = codeSource.getLocation();
        String fileName = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);

        Matcher matcher = JAR_VERSION.matcher(fileName);
        // Asserted rather than defaulted: a jar name this cannot parse must fail loudly, otherwise the
        // check above quietly degrades into comparing placeholder values.
        assertThat(matcher.matches())
                .as("Could not read a version out of '%s' (for %s)", fileName, type.getName())
                .isTrue();
        return matcher.group("version");
    }
}
