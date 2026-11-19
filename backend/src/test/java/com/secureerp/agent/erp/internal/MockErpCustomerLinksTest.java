package com.secureerp.agent.erp.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.identity.spi.ChannelIdentity;
import com.secureerp.agent.identity.spi.CustomerLinkUnavailableException;
import com.secureerp.agent.identity.spi.ErpCustomerLink;
import org.junit.jupiter.api.Test;

/** ERP_CONNECTOR §3.1 (EP-04): exact binding lookup only, typed failure without ERP text. */
class MockErpCustomerLinksTest {

    private final MockErpCustomerLinks links = new MockErpCustomerLinks(MockErpFaultMode.NONE);

    @Test
    void onlyTheExactWebChatBindingIsLinked() {
        assertThat(links.findByChannelBinding(ChannelIdentity.of("WEB_CHAT", "cust-a")))
                .contains(new ErpCustomerLink("SYNTH-CUST-A"));
        assertThat(links.findByChannelBinding(ChannelIdentity.of("ZALO", "cust-a"))).isEmpty();
        assertThat(links.findByChannelBinding(ChannelIdentity.of("WEB_CHAT", "cust-new"))).isEmpty();
        assertThat(links.findByChannelBinding(ChannelIdentity.of("WEB_CHAT", "sales-1"))).isEmpty();
        assertThat(links.findByChannelBinding(null)).isEmpty();
    }

    @Test
    void backendFailureIsTypedAndCarriesNoErpText() {
        for (MockErpFaultMode mode : new MockErpFaultMode[] {MockErpFaultMode.ERROR_WITH_SENTINEL,
                MockErpFaultMode.TIMEOUT}) {
            MockErpCustomerLinks failing = new MockErpCustomerLinks(mode);
            assertThatThrownBy(() -> failing.findByChannelBinding(ChannelIdentity.of("WEB_CHAT", "cust-a")))
                    .isInstanceOf(CustomerLinkUnavailableException.class)
                    .hasMessage("CUSTOMER_LINK_UNAVAILABLE")
                    .hasNoCause();
        }
    }
}
