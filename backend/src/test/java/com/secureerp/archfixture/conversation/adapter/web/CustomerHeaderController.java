package com.secureerp.archfixture.conversation.adapter.web;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation: customer identity from a request header. */
@RestController
public class CustomerHeaderController {

    public String chat(@RequestHeader("X-Customer-Id") String header) {
        return header;
    }
}
