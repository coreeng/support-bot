package com.coreeng.supportbot.ticket;

/**
 * Internal structure for writing assignee data to the database.
 *
 * @param value The encrypted or plain value to write (null to skip assignment write)
 * @param format "plain" or "enc_v1" indicating storage format
 * @param orphaned True if assignee couldn't be encrypted/decrypted properly
 * @param hash SHA-256 hash of the plain user ID (for searchable filtering)
 */
record AssigneeWrite(String value, String format, boolean orphaned, String hash) {
}

