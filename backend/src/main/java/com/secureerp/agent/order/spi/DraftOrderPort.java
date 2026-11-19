package com.secureerp.agent.order.spi;

import java.util.Optional;

/**
 * Draft orders in the ERP (ERP_CONNECTOR §3.6, BRD §12 {@code createDraftOrder} /
 * {@code findDraftByExternalRef}). There is deliberately no way to approve, cancel or reprice an
 * order (BR-20, ARCH-09). What the target ERP really offers is OPEN (D-01..D-03).
 */
public interface DraftOrderPort {

    /**
     * Asks the ERP once to create the draft. Never retries. Must not throw for an ERP failure: a
     * timeout or an unclassifiable answer is {@link DraftOrderOutcome.Unknown}.
     *
     * @param idempotencyKey present only when the configured capability says the ERP supports it
     *                       (BR-18); the adapter does not decide that
     */
    DraftOrderOutcome create(DraftOrderCommand command, Optional<IdempotencyKey> idempotencyKey);

    /** Called only when the configured capability says the ERP supports the lookup (T-ERP-05). */
    Optional<DraftOrderStatus> findByExternalRef(ExternalRef externalRef);
}
