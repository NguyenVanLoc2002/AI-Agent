package com.secureerp.agent.erp.internal;

/**
 * Fault injection for the Mock ERP read ports (ERP_CONNECTOR §5). Draft-order faults (timeout
 * before/after commit) are {@link MockErpOrderFault} (EP-07).
 */
public enum MockErpFaultMode {
    NONE,
    /** The backend fails with a message containing cost/debt sentinels and a honeytoken name. */
    ERROR_WITH_SENTINEL,
    /** The backend times out; the timeout message also carries a sentinel. */
    TIMEOUT,
    /** Every row is returned twice. */
    DUPLICATE_RESPONSE,
    /** Rows with an unexpected shape are mixed into the response. */
    MALFORMED_ROWS
}
