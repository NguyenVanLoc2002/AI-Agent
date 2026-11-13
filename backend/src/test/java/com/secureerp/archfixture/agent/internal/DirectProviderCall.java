package com.secureerp.archfixture.agent.internal;

import com.openai.archfixture.FakeProviderClient;

/** Violation ARCH-01: provider SDK outside gateway.adapter. */
public class DirectProviderCall {
    public String call() {
        return new FakeProviderClient().complete("bypass gateway");
    }
}
