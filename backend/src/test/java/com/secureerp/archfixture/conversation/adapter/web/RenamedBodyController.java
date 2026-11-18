package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.conversation.api.RenamedBodies;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violations (third review M2): a customer ID under a Jackson wire name. */
@RestController
public class RenamedBodyController {

    public String byProperty(@RequestBody RenamedBodies.ByProperty body) {
        return body.text();
    }

    public String byAlias(@RequestBody RenamedBodies.ByAlias body) {
        return body.text();
    }
}
