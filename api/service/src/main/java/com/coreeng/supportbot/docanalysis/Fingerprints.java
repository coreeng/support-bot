package com.coreeng.supportbot.docanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Fingerprints {

    private Fingerprints() {}

    /** SHA-256 of the parts, each length-prefixed so that no two different lists digest alike. */
    public static String of(String... parts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available in every JVM", e);
        }
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
