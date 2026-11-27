package com.coreeng.supportbot.docanalysis;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A SHA-256 over parts, each prefixed with its length so that {@code ["ab", "c"]} and
 * {@code ["a", "bc"]} differ.
 */
public final class Fingerprint {

    private Fingerprint() {}

    public static String of(List<String> parts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available in every JVM", e);
        }
        for (String part : parts) {
            byte[] bytes = part.getBytes(UTF_8);
            digest.update(
                    ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
