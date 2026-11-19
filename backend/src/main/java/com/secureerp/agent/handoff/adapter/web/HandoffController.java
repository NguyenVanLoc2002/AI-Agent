package com.secureerp.agent.handoff.adapter.web;

import com.secureerp.agent.handoff.HandoffInbox;
import com.secureerp.agent.identity.CurrentIdentity;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff inbox (SYSTEM_ARCHITECTURE §8, UC-06). Transport only: the staff identity and team come from
 * the session; the role, the team and the handoff's state are checked by the service. Registered with
 * profile {@code demo} only (the only users are the synthetic demo users); {@code /api/internal/**}
 * is SALES only at the URL level as well.
 */
@RestController
@Profile("demo")
@RequestMapping(path = "/api/internal/handoffs", produces = MediaType.APPLICATION_JSON_VALUE)
class HandoffController {

    private final CurrentIdentity identity;
    private final HandoffInbox inbox;

    HandoffController(CurrentIdentity identity, HandoffInbox inbox) {
        this.identity = identity;
        this.inbox = inbox;
    }

    @GetMapping
    List<HandoffView> list() {
        return inbox.list(session()).stream().map(HandoffView::of).toList();
    }

    @PostMapping("/{handoffId}/claim")
    HandoffView claim(@PathVariable long handoffId) {
        return HandoffView.of(inbox.claim(session(), handoffId));
    }

    @PostMapping("/{handoffId}/resolve")
    HandoffView resolve(@PathVariable long handoffId) {
        return HandoffView.of(inbox.resolve(session(), handoffId));
    }

    private SessionIdentity session() {
        return identity.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    /** The reason is an internal code for staff; nothing here identifies the customer. */
    record HandoffView(long handoffId, String conversationId, String reasonCode, String status, String openedAt,
            boolean mine, String followUpReasonCode) {

        static HandoffView of(HandoffInbox.Item item) {
            return new HandoffView(item.handoffId(), item.conversation().value(), item.reasonCode(),
                    item.status().name(), item.openedAt().toString(), item.mine(), item.followUpReasonCode());
        }
    }
}
