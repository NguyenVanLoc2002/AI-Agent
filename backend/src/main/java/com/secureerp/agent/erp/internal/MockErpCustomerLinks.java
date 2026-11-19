package com.secureerp.agent.erp.internal;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.identity.spi.CustomerLinkPort;
import com.secureerp.agent.identity.spi.CustomerLinkUnavailableException;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mock ERP channel-binding table (ERP_CONNECTOR §3.1, EP-04; A-19/A-37). PoC: a static seed for the
 * synthetic web-chat demo users of profile {@code demo}; {@code cust-new} is deliberately absent (no ERP
 * link). Lookup by exact binding only, no search (rule 10). Whether a real ERP offers this lookup is
 * OPEN (D-01, D-06).
 */
@Component
class MockErpCustomerLinks implements CustomerLinkPort {

    private static final String WEB_CHAT = "WEB_CHAT";

    private static final Map<ChannelIdentity, String> LINKS = Map.of(
            ChannelIdentity.of(WEB_CHAT, "cust-a"), MockErpBackend.CUSTOMER_A,
            ChannelIdentity.of(WEB_CHAT, "cust-b"), MockErpBackend.CUSTOMER_B,
            ChannelIdentity.of(WEB_CHAT, "cust-c"), MockErpBackend.CUSTOMER_C);

    private final MockErpFaultMode faultMode;

    MockErpCustomerLinks(@Value("${apas.erp.mock.fault-mode:NONE}") MockErpFaultMode faultMode) {
        this.faultMode = Objects.requireNonNull(faultMode, "faultMode");
    }

    @Override
    public Optional<ErpCustomerLink> findByChannelBinding(ChannelIdentity binding) {
        if (faultMode == MockErpFaultMode.ERROR_WITH_SENTINEL || faultMode == MockErpFaultMode.TIMEOUT) {
            throw new CustomerLinkUnavailableException();
        }
        if (binding == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LINKS.get(binding)).map(ErpCustomerLink::new);
    }
}
