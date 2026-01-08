package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Helper for assignee encryption/decryption.
 *
 * Supports:
 * - plain mode (encryption disabled)
 * - AES-GCM mode (enc_v1 prefix) when enabled + key provided
 *
 * The methods are fail-safe: on any error, they return null and log,
 * letting callers degrade gracefully without breaking the flow.
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class AssigneeCrypto {

    private static final String ENC_PREFIX = "enc_v1:";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // bytes

    private final TicketAssignmentProps props;
    private final SecureRandom secureRandom = new SecureRandom();

    public Optional<EncryptResult> encrypt(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (!encryptionEnabled()) {
            return Optional.of(new EncryptResult(userId, "plain"));
        }

        SecretKey key = deriveKey();
        if (key == null) {
            log.warn("Assignment encryption enabled but key is missing/invalid; skipping assignment encryption");
            return Optional.empty();
        }

        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(userId.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            String encoded = ENC_PREFIX + Base64.getEncoder().encodeToString(payload);
            return Optional.of(new EncryptResult(encoded, "enc_v1"));
        } catch (Exception e) {
            log.warn("Failed to encrypt assignee: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> decrypt(String stored, String format) {
        if (stored == null) {
            return Optional.empty();
        }
        if (format == null || "plain".equalsIgnoreCase(format)) {
            return Optional.of(stored);
        }
        if (!"enc_v1".equalsIgnoreCase(format)) {
            log.warn("Unknown assignee format: {}", format);
            return Optional.empty();
        }
        if (!encryptionEnabled()) {
            log.warn("Assignee stored encrypted but encryption is disabled; cannot decrypt");
            return Optional.empty();
        }

        SecretKey key = deriveKey();
        if (key == null) {
            log.warn("Assignment encryption enabled but key is missing/invalid; cannot decrypt");
            return Optional.empty();
        }

        String payloadStr = stored.startsWith(ENC_PREFIX) ? stored.substring(ENC_PREFIX.length()) : stored;
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(payloadStr);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to base64-decode assignee payload: {}", e.getMessage());
            return Optional.empty();
        }

        if (payload.length <= IV_LENGTH) {
            log.warn("Invalid payload length for encrypted assignee");
            return Optional.empty();
        }

        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
        System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);

        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return Optional.of(new String(plaintext, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            log.warn("Failed to decrypt assignee: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean encryptionEnabled() {
        return props != null
            && props.encryption() != null
            && props.encryption().enabled();
    }

    private SecretKey deriveKey() {
        String keyStr = props.encryption() != null ? props.encryption().key() : null;
        if (keyStr == null || keyStr.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(keyStr.getBytes(StandardCharsets.UTF_8)); // 32 bytes
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            log.warn("Failed to derive assignment encryption key: {}", e.getMessage());
            return null;
        }
    }

}
