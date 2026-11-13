package com.secureerp.agent.platform;

import java.util.Objects;

/**
 * Base exception for failures that modules want mapped to a stable {@link ErrorCode}.
 *
 * <p>The exception message is never sent to clients or written to logs by the error handler,
 * because messages can accidentally carry sensitive values. Only {@link #code()} is exposed.
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    public ApplicationException(ErrorCode code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public ApplicationException(ErrorCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code").name(), cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
