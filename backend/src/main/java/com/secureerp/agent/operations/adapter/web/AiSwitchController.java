package com.secureerp.agent.operations.adapter.web;

import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.operations.AiSwitchAdmin;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.KillSwitch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/ops/kill-switch} (decision 3A). Transport only: identity from the session, role checked
 * by the service and at the URL ({@code /api/ops/**} OPS only). Profile {@code demo} only, like every
 * endpoint used by the synthetic demo users (ADR-015).
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/ops/kill-switch", produces = MediaType.APPLICATION_JSON_VALUE)
class AiSwitchController {

    private final CurrentIdentity identity;
    private final AiSwitchAdmin admin;

    AiSwitchController(CurrentIdentity identity, AiSwitchAdmin admin) {
        this.identity = identity;
        this.admin = admin;
    }

    @GetMapping
    SwitchView state() {
        return SwitchView.of(admin.state(session()));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    SwitchView set(@Valid @RequestBody SwitchRequest request) {
        return SwitchView.of(admin.set(session(), request.enabled()));
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    record SwitchRequest(@NotNull Boolean enabled) {
    }

    /** {@code reason}: why the AI is off (OPERATOR, PROVIDER_AUTH, UNREADABLE), null when on. */
    record SwitchView(boolean aiEnabled, String reason, String since) {

        static SwitchView of(KillSwitch.State state) {
            return new SwitchView(state.aiEnabled(),
                    state.disabledReason() == null ? null : state.disabledReason().name(),
                    state.since() == null ? null : state.since().toString());
        }
    }
}
