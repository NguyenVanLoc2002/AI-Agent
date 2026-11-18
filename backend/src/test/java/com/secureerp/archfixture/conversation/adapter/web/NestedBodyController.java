package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.conversation.api.OrderEnvelope;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation (second review M4): body type outside adapter.web with a nested buyer ID. */
@RestController
public class NestedBodyController {

    public String submit(@RequestBody OrderEnvelope envelope) {
        return envelope.note();
    }
}
