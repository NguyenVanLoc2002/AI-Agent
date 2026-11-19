package com.secureerp.agent.order.spi;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What the adapter knows after asking the ERP to create a draft order (ERP_CONNECTOR §3.6). A
 * timeout or any answer the adapter cannot classify is {@link Unknown}, never a failure: the ERP may
 * have written the order (BR-19). None of the variants carries an ERP message.
 */
public sealed interface DraftOrderOutcome {

    Pattern ERP_ORDER_REF = Pattern.compile("^[A-Za-z0-9:_.-]{1,64}$");

    /** @param erpOrderRef the ERP's own number of the draft */
    record Created(String erpOrderRef) implements DraftOrderOutcome {

        public Created {
            if (erpOrderRef == null || !ERP_ORDER_REF.matcher(erpOrderRef).matches()) {
                throw new IllegalArgumentException("invalid erp order reference format");
            }
        }
    }

    /** The ERP refused the order with a structured code and wrote nothing. */
    record Rejected(Rejection code) implements DraftOrderOutcome {

        public Rejected {
            Objects.requireNonNull(code, "code");
        }
    }

    record Unknown(UnknownReason reason) implements DraftOrderOutcome {

        public Unknown {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Structured rejection codes the adapter maps from an allowlist; anything else is {@link Unknown}. */
    enum Rejection {
        CUSTOMER_NOT_ALLOWED,
        INVALID_ORDER
    }

    enum UnknownReason {
        TIMEOUT,
        /** An error or response the adapter cannot tell from a technical failure. */
        UNCLASSIFIED
    }
}
