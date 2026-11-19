package com.secureerp.agent.platform;

import org.springframework.http.HttpStatus;

/**
 * Stable, technical error codes returned in RFC 9457 problem responses.
 *
 * <p>Only transport-level codes are defined in POC-001. Business codes (for example
 * {@code QUOTE_STALE_VERSION}) are added by the issue that implements the behaviour.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    /** EP-04 (decision 2026-11-19): a customer session without an ERP link cannot start a conversation. */
    CUSTOMER_NOT_LINKED(HttpStatus.FORBIDDEN, "Customer not linked"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    /** EP-07 (DATA_FLOW §2): the confirmed version is not the quote's current one. */
    QUOTE_STALE_VERSION(HttpStatus.CONFLICT, "Quote version is not current"),
    /** EP-07 (BR-16): the price changed at confirmation; a new version was issued and needs a fresh confirmation. */
    QUOTE_REPRICED(HttpStatus.CONFLICT, "Quote was repriced"),
    /** EP-07 (AC-04.2): the quote's validity has passed. */
    QUOTE_EXPIRED(HttpStatus.GONE, "Quote expired"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Payload too large"),
    /** Any other 4xx; the response keeps the original status. */
    REQUEST_FAILED(HttpStatus.BAD_REQUEST, "Request failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
