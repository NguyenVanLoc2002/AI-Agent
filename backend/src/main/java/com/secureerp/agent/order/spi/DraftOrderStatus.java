package com.secureerp.agent.order.spi;

/** A draft the ERP found by our reference (ERP_CONNECTOR §3.6). */
public record DraftOrderStatus(String erpOrderRef) {

    public DraftOrderStatus {
        if (erpOrderRef == null || !DraftOrderOutcome.ERP_ORDER_REF.matcher(erpOrderRef).matches()) {
            throw new IllegalArgumentException("invalid erp order reference format");
        }
    }
}
