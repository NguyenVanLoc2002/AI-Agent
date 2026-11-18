package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation: customer reference taken from the request. */
@RestController
public class CustomerRefController {

    public String chat(AuthenticatedCustomerRef customer, String text) {
        return text;
    }
}
