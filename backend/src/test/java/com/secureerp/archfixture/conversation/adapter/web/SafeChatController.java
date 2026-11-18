package com.secureerp.archfixture.conversation.adapter.web;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Compliant controller: identity comes from the server-side session, not the request. */
@RestController
public class SafeChatController {

    public String chat(@RequestBody String text) {
        return text;
    }
}
