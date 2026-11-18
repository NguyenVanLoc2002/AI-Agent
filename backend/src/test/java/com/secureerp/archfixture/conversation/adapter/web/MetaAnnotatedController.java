package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;

/** ARCH-08b violation behind a meta-annotated stereotype. */
@ApiController
public class MetaAnnotatedController {

    public String chat(AuthenticatedCustomerRef customer) {
        return "ok";
    }
}
