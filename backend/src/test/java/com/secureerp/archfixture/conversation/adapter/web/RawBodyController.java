package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.conversation.api.OrderEnvelope;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** ARCH-08b violations (third review M2): array bodies, JSON trees and raw bytes. */
@RestController
public class RawBodyController {

    public int buyers(@RequestBody OrderEnvelope.Buyer[] buyers) {
        return buyers.length;
    }

    public String tree(@RequestBody JsonNode body) {
        return body.toString();
    }

    public int bytes(@RequestBody byte[] body) {
        return body.length;
    }
}
