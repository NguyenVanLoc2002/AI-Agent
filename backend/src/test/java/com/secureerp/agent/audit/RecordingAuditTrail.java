package com.secureerp.agent.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test double: keeps what was recorded, split by the two write modes of {@link AuditTrail}. */
public class RecordingAuditTrail implements AuditTrail {

    private final List<AuditEvent> joined = new CopyOnWriteArrayList<>();
    private final List<AuditEvent> separate = new CopyOnWriteArrayList<>();

    /** Like the real writer ({@code MANDATORY}): a joined record needs the transaction of the change. */
    @Override
    public void record(AuditEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("record needs the transaction of the change");
        }
        joined.add(event);
    }

    @Override
    public void recordSeparately(AuditEvent event) {
        separate.add(event);
    }

    public List<AuditEvent> joined() {
        return List.copyOf(joined);
    }

    public List<AuditEvent> separate() {
        return List.copyOf(separate);
    }
}
