package com.secureerp.archfixture.agent.internal;

import com.secureerp.archfixture.gateway.inbound.ModelResponse;

/** Compliant ARCH-14: the agent reads model output and validates it. */
public class ValidatesModelOutput {
    public boolean valid(ModelResponse response) {
        return response.text() != null;
    }
}
