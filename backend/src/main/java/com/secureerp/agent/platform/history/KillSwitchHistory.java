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
     * @param id     the row key, used for keyset paging only
     * @param reason {@code null} when the change switched the AI on
     * @param actor  the operator's pseudonym; {@code null} for the system (the provider-auth breaker)
     */
    record Change(long id, boolean aiEnabled, KillSwitch.Reason reason, String actor, Instant occurredAt) {
    }

    /**
     * Changes with {@code from <= occurredAt <= to}, newest first ({@code occurredAt}, then {@code id}).
     *
     * @param beforeAt {@code null} for the first page; else only changes with {@code occurredAt < beforeAt}, or
     *                 {@code occurredAt == beforeAt} and {@code id < beforeId} (AUDIT-FLOOD keyset paging)
     */
    List<Change> changes(Instant from, Instant to, Instant beforeAt, long beforeId, int limit);
}
