package com.secureerp.agent.gateway.adapter;

/**
 * Capture point (a) of ADR-016: called with the exact request body of every send attempt
 * (including retries), immediately before it is handed to the HTTP client. No compression is
 * applied after this point. Production registers no listener; tests use it to compare with what the
 * proxy and the Fake provider received.
 */
@FunctionalInterface
public interface EgressCaptureListener {

    void onAttempt(byte[] requestBody);
}
