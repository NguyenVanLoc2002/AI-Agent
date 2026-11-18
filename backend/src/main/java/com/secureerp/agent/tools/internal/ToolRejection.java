package com.secureerp.agent.tools.internal;

import com.secureerp.agent.gateway.api.ToolErrorResult;
import java.util.Objects;

/** A refused tool call: a stable code, never the offending value (no message, no stack trace). */
final class ToolRejection extends RuntimeException {

    private final ToolErrorResult.Code code;
    private final boolean handoff;

    ToolRejection(ToolErrorResult.Code code, boolean handoff) {
        super(Objects.requireNonNull(code, "code").name(), null, false, false);
        this.code = code;
        this.handoff = handoff;
    }

    static ToolRejection of(ToolErrorResult.Code code) {
        return new ToolRejection(code, false);
    }

    ToolErrorResult.Code code() {
        return code;
    }

    /** The rejection itself requires a staff handoff (e.g. the customer asked for one). */
    boolean handoff() {
        return handoff;
    }
}
