package com.secureerp.agent.operations;

import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.KillSwitch;

/**
 * The operator's view of the AI kill switch (UC-08, decisions 3A–5A). Failures are
 * {@code ApplicationException} codes: 403 for anyone but OPS. Every change is recorded with the
 * operator's pseudonymous principal.
 */
public interface AiSwitchAdmin {

    KillSwitch.State state(SessionIdentity operator);

    /** Off: new turns go to staff (AI_DISABLED). On: the AI answers again, an open provider-auth breaker closes. */
    KillSwitch.State set(SessionIdentity operator, boolean enabled);
}
