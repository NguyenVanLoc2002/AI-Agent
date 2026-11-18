package com.secureerp.archfixture.conversation.adapter.web;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation: customer ID bound from a request parameter. */
@RestController
public class CustomerIdController {

    public String quote(@RequestParam("customerId") String c) {
        return c;
    }
}
