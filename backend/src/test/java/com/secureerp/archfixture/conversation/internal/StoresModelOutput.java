package com.secureerp.archfixture.conversation.internal;

import com.secureerp.archfixture.gateway.inbound.ModelResponse;

/** Violates ARCH-14: the conversation would store raw model output. */
public class StoresModelOutput {
    public String store(ModelResponse response) {
        return response.text();
    }
}
