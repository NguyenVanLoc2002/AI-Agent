package com.secureerp.agent.platform.history;

import com.secureerp.agent.platform.KillSwitch;
import java.time.Instant;
import java.util.List;

/**
 * Read side of {@code feature_switch_event} for the security inspector (EP-09b, decision 4A: the switch
 * history stays in platform, written atomically with the switch; no copy in {@code audit_event}).
 */
public interface KillSwitchHistory {

    /**
     * @param reason {@code null} when the change switched the AI on
     * @param actor  the operator's pseudonym; {@code null} for the system (the provider-auth breaker)
     */
    record Change(boolean aiEnabled, KillSwitch.Reason reason, String actor, Instant occurredAt) {
    }

    /** Changes with {@code from <= occurredAt <= to}, newest first. */
    List<Change> changes(Instant from, Instant to, int limit);
}
