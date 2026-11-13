package com.secureerp.archfixture.gateway.adapter;

import com.openai.archfixture.FakeProviderClient;

/** Compliant: SDK use inside gateway.adapter (ARCH-01). */
public class ProviderAdapter {
    private final FakeProviderClient client = new FakeProviderClient();

    public String call() {
        return client.complete("safe");
    }
}
