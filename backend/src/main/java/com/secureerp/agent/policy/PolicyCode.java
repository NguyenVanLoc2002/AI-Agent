package com.secureerp.agent.policy;

/**
 * Stable reason codes for DLP decisions (AI_GATEWAY §2.4). Safe to log, audit and show to staff;
 * they never contain the detected value.
 */
public enum PolicyCode {
    /** A registered synthetic sentinel (honeytoken) was found, in any supported variant. */
    HONEYTOKEN,
    /** An amount of money in price/currency context. */
    MONEY,
    PHONE,
    EMAIL,
    /** A full 17-character VIN; only the masked WMI form may leave (A-11). */
    FULL_VIN,
    /** A JSON property outside the egress allowlist ("reject on unclassified fields"). */
    UNCLASSIFIED_FIELD,
    PAYLOAD_TOO_LARGE,
    MALFORMED_PAYLOAD
}
