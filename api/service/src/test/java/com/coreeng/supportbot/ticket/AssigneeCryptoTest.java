package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AssigneeCryptoTest {

    @Test
    public void shouldReturnPlainWhenEncryptionDisabled() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(false, null)
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        var result = crypto.encrypt(userId);

        // then
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().value());
        assertEquals("plain", result.get().format());
    }

    @Test
    public void shouldReturnEmptyWhenEncryptionEnabledButNoKey() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, null)
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        var result = crypto.encrypt(userId);

        // then
        assertTrue(result.isEmpty(), "Should return empty when key is missing");
    }

    @Test
    public void shouldReturnEmptyWhenEncryptionEnabledButBlankKey() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "   ")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        var result = crypto.encrypt(userId);

        // then
        assertTrue(result.isEmpty(), "Should return empty when key is blank");
    }

    @Test
    public void shouldReturnEmptyWhenUserIdIsNull() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "test-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);

        // when
        var result = crypto.encrypt(null);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldEncryptWhenEncryptionEnabledWithValidKey() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key-123")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        var result = crypto.encrypt(userId);

        // then
        assertTrue(result.isPresent());
        assertNotNull(result.get().value());
        assertTrue(result.get().value().startsWith("enc_v1:"), "Encrypted value should have enc_v1 prefix");
        assertNotEquals(userId, result.get().value(), "Encrypted value should differ from plaintext");
        assertEquals("enc_v1", result.get().format());
    }

    @Test
    public void shouldRoundTripEncryptAndDecrypt() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key-123")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String originalUserId = "U987654321";

        // when
        var encrypted = crypto.encrypt(originalUserId);
        var decrypted = encrypted.flatMap(e -> crypto.decrypt(e.value(), e.format()));

        // then
        assertTrue(encrypted.isPresent());
        assertTrue(decrypted.isPresent());
        assertEquals(originalUserId, decrypted.get(), "Decrypted value should match original");
    }

    @Test
    public void shouldDecryptPlainValue() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(false, null)
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String plainUserId = "U123456";

        // when
        var decrypted = crypto.decrypt(plainUserId, "plain");

        // then
        assertTrue(decrypted.isPresent());
        assertEquals(plainUserId, decrypted.get());
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingWithWrongKey() {
        // given
        TicketAssignmentProps encryptProps = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "key-1")
        );
        AssigneeCrypto cryptoEncrypt = new AssigneeCrypto(encryptProps);
        String userId = "U123456";
        var encrypted = cryptoEncrypt.encrypt(userId);
        assertTrue(encrypted.isPresent());

        TicketAssignmentProps decryptProps = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "different-key-2")
        );
        AssigneeCrypto cryptoDecrypt = new AssigneeCrypto(decryptProps);

        // when
        var decrypted = cryptoDecrypt.decrypt(encrypted.get().value(), encrypted.get().format());

        // then
        assertTrue(decrypted.isEmpty(), "Decryption with wrong key should fail gracefully");
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingEncryptedValueButEncryptionDisabled() {
        // given
        TicketAssignmentProps encryptProps = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto cryptoEncrypt = new AssigneeCrypto(encryptProps);
        String userId = "U123456";
        var encrypted = cryptoEncrypt.encrypt(userId);
        assertTrue(encrypted.isPresent());

        TicketAssignmentProps decryptProps = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(false, null)
        );
        AssigneeCrypto cryptoDecrypt = new AssigneeCrypto(decryptProps);

        // when
        var decrypted = cryptoDecrypt.decrypt(encrypted.get().value(), encrypted.get().format());

        // then
        assertTrue(decrypted.isEmpty(), "Should not decrypt when encryption is disabled");
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingWithUnknownFormat() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);

        // when
        var decrypted = crypto.decrypt("some-value", "unknown_format");

        // then
        assertTrue(decrypted.isEmpty(), "Should return empty for unknown format");
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingNullValue() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);

        // when
        var decrypted = crypto.decrypt(null, "enc_v1");

        // then
        assertTrue(decrypted.isEmpty());
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingCorruptedPayload() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String corruptedValue = "enc_v1:not-valid-base64!@#$";

        // when
        var decrypted = crypto.decrypt(corruptedValue, "enc_v1");

        // then
        assertTrue(decrypted.isEmpty(), "Should return empty for corrupted base64");
    }

    @Test
    public void shouldReturnEmptyWhenDecryptingTooShortPayload() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        // Base64 encoding of a very short byte array (shorter than IV length of 12 bytes)
        String shortPayload = "enc_v1:AQID"; // only 3 bytes

        // when
        var decrypted = crypto.decrypt(shortPayload, "enc_v1");

        // then
        assertTrue(decrypted.isEmpty(), "Should return empty when payload is too short");
    }

    @Test
    public void shouldHandleDecryptWithNullFormat() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(false, null)
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String plainValue = "U123456";

        // when
        var decrypted = crypto.decrypt(plainValue, null);

        // then
        assertTrue(decrypted.isPresent());
        assertEquals(plainValue, decrypted.get(), "Null format should be treated as plain");
    }

    @Test
    public void shouldProduceDifferentCiphertextsForSameInput() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        var result1 = crypto.encrypt(userId);
        var result2 = crypto.encrypt(userId);

        // then
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertNotEquals(result1.get().value(), result2.get().value(),
            "Each encryption should use unique IV, producing different ciphertext");
        
        // but both should decrypt to the same value
        var decrypted1 = crypto.decrypt(result1.get().value(), result1.get().format());
        var decrypted2 = crypto.decrypt(result2.get().value(), result2.get().format());
        assertTrue(decrypted1.isPresent());
        assertTrue(decrypted2.isPresent());
        assertEquals(userId, decrypted1.get());
        assertEquals(userId, decrypted2.get());
    }

    @Test
    public void shouldComputeHmacHashWhenKeyIsPresent() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        String hash = crypto.computeHash(userId);

        // then
        assertNotNull(hash, "Hash should be computed when key is present");
        assertEquals(64, hash.length(), "HMAC-SHA256 should produce 64 hex chars");
        assertNotEquals(userId, hash, "Hash should differ from input");
    }

    @Test
    public void shouldReturnNullHashWhenKeyIsNull() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(false, null)
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        String hash = crypto.computeHash(userId);

        // then
        assertNull(hash, "Hash should be null when encryption key is null");
    }

    @Test
    public void shouldReturnNullHashWhenKeyIsBlank() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "   ")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        String hash = crypto.computeHash(userId);

        // then
        assertNull(hash, "Hash should be null when encryption key is blank");
    }

    @Test
    public void shouldReturnNullHashWhenUserIdIsNull() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);

        // when
        String hash = crypto.computeHash(null);

        // then
        assertNull(hash, "Hash should be null when userId is null");
    }

    @Test
    public void shouldProduceDeterministicHmacHash() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);
        String userId = "U123456";

        // when
        String hash1 = crypto.computeHash(userId);
        String hash2 = crypto.computeHash(userId);

        // then
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2, "HMAC hash should be deterministic for same input and key");
    }

    @Test
    public void shouldProduceDifferentHashesForDifferentInputs() {
        // given
        TicketAssignmentProps props = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "my-secret-key")
        );
        AssigneeCrypto crypto = new AssigneeCrypto(props);

        // when
        String hash1 = crypto.computeHash("U123456");
        String hash2 = crypto.computeHash("U789012");

        // then
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, "Different user IDs should produce different hashes");
    }

    @Test
    public void shouldProduceDifferentHashesForDifferentKeys() {
        // given
        TicketAssignmentProps props1 = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "key-1")
        );
        AssigneeCrypto crypto1 = new AssigneeCrypto(props1);

        TicketAssignmentProps props2 = new TicketAssignmentProps(
            true,
            new TicketAssignmentProps.Encryption(true, "key-2")
        );
        AssigneeCrypto crypto2 = new AssigneeCrypto(props2);
        String userId = "U123456";

        // when
        String hash1 = crypto1.computeHash(userId);
        String hash2 = crypto2.computeHash(userId);

        // then
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2, 
            "HMAC should produce different hashes with different keys (prevents rainbow table attacks)");
    }
}

