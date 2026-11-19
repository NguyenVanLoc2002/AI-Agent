package com.secureerp.agent.erp.internal;

/** Fault injection for draft orders in the Mock ERP (ERP_CONNECTOR §5, EP-07). Set by tests only. */
public enum MockErpOrderFault {
    NONE,
    /** The call times out and the ERP has written nothing. */
    TIMEOUT_BEFORE_COMMIT,
    /** The ERP writes the draft, then the answer is lost (SEC-04). The timeout message carries a sentinel. */
    TIMEOUT_AFTER_COMMIT,
    /** The ERP refuses with a structured code; its message carries sentinels. */
    REJECT,
    /** The ERP refuses with a code the adapter does not know. */
    UNLISTED_ERROR,
    /** The ERP writes the draft and answers with a row of an unexpected shape. */
    MALFORMED_RESPONSE,
    /** The call fails with an exception whose message carries sentinels; nothing is written. */
    ERROR_WITH_SENTINEL
}
