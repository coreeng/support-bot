package com.coreeng.supportbot.ticket;

/**
 * Result of encrypting an assignee user ID.
 *
 * @param value The encrypted or plain value to store
 * @param format "plain" or "enc_v1" indicating storage format
 */
public record EncryptResult(String value, String format) {
}

