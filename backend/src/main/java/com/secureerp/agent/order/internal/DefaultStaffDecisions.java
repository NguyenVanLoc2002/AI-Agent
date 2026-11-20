package com.secureerp.agent.order.internal;

import com.secureerp.agent.audit.AuditActor;
import com.secureerp.agent.audit.AuditEvent;
import com.secureerp.agent.audit.AuditTrail;
import com.secureerp.agent.audit.DeniedInTransaction;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.order.DraftOrderMirrorRefused;
import com.secureerp.agent.order.DraftOrderState;
import com.secureerp.agent.order.DraftOrderStateChanged;
import com.secureerp.agent.order.StaffDecisions;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records the staff decision on a created draft order (EP-08b). One transaction: a conditional update
 * on the expected state (two writers cannot both win), the event row with the actor, and the quote
 * mirror ({@link DraftOrderStateChanged}); a quote that cannot follow rolls everything back. Logs carry
 * states and codes only.
 */
@Service
class DefaultStaffDecisions implements StaffDecisions {

    private static final Logger log = LoggerFactory.getLogger(DefaultStaffDecisions.class);
    static final int MAX_LISTED = 50;
    private static final Set<DraftOrderState> DECIDABLE =
            Set.of(DraftOrderState.CREATED, DraftOrderState.RECONCILED_CREATED);

    private final DraftOrderStore store;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AuditTrail audit;

    DefaultStaffDecisions(DraftOrderStore store, TransactionTemplate transactions, ApplicationEventPublisher events,
            Clock clock, AuditTrail audit) {
        this.audit = audit;
        this.store = store;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public List<AwaitingDecision> awaiting(SessionIdentity staff) {
        Team team = SalesStaff.teamOf(staff, "staff_decision", audit);
        return store.awaitingDecision(team.value(), MAX_LISTED).stream()
                .map(request -> new AwaitingDecision(request.id(), request.quoteRef(), request.quoteVersion(),
                        request.erpOrderRef(), request.completedAt()))
                .toList();
    }

    @Override
    public DraftOrderState decide(SessionIdentity staff, long requestId, Decision decision) {
        Team team = SalesStaff.teamOf(staff, "staff_decision", audit);
        Objects.requireNonNull(decision, "decision");
        DraftOrderState target = decision == Decision.APPROVED
                ? DraftOrderState.STAFF_APPROVED : DraftOrderState.STAFF_REJECTED;
        String actor = staff.principal().pseudonym();
        AuditActor auditActor = staff.auditActor();
        try {
            return transactions.execute(tx -> decide(requestId, team, target, actor, auditActor, clock.instant()));
        } catch (DeniedInTransaction denied) {
            // Recorded after the transaction returned its connection (EP-09b reviews M-1).
            audit.recordSeparately(denied.event());
            throw new ApplicationException(ErrorCode.NOT_FOUND);
        } catch (DraftOrderMirrorRefused quoteCannotFollow) {
            // Rolled back: request and quote disagree; someone has to look before a decision is recorded.
            log.error("order.staff_decision refused code=CONFLICT reason=MIRROR_REFUSED");
            throw new ApplicationException(ErrorCode.CONFLICT);
        } catch (PessimisticLockingFailureException busy) {
            log.warn("order.staff_decision refused code=CONFLICT reason=LOCK_BUSY");
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    private DraftOrderState decide(long requestId, Team team, DraftOrderState target, String actor,
            AuditActor auditActor, Instant now) {
        // Another team's request and a missing one are the same 404.
        DraftOrderStore.Request request = store.findForTeam(requestId, team.value()).orElseThrow(() -> {
            log.warn("order.staff_decision denied code=FORBIDDEN_RESOURCE");
            return new DeniedInTransaction(AuditEvent.denied(AuditEvent.Resource.DRAFT_ORDER,
                    AuditEvent.FORBIDDEN_RESOURCE, auditActor));
        });
        if (request.state() == target) {
            return target; // the same decision again
        }
        if (!DECIDABLE.contains(request.state()) || !store.decide(requestId, request.state(), target)) {
            // Not waiting for a decision, decided the other way, or a concurrent decision won meanwhile.
            boolean sameDecisionWon = store.findForTeam(requestId, team.value())
                    .map(DraftOrderStore.Request::state).filter(target::equals).isPresent();
            if (sameDecisionWon) {
                return target;
            }
            log.warn("order.staff_decision refused code=CONFLICT state={}", request.state());
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        store.event(requestId, target.name(), actor, now);
        // EP-09b (A-43(n)): committed with the decision (decision 2A).
        audit.record(new AuditEvent(AuditEvent.Action.STAFF_ORDER_DECIDED, AuditEvent.Resource.DRAFT_ORDER,
                target.name(), auditActor));
        events.publishEvent(new DraftOrderStateChanged(request.quoteRef(), request.quoteVersion(), target));
        log.info("order.staff_decision state={}", target);
        return target;
    }
}
