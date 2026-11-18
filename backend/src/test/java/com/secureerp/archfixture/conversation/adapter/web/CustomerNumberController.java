package com.secureerp.archfixture.conversation.adapter.web;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation (second review M4): customer identity under a name the old regex missed. */
@RestController
public class CustomerNumberController {

    public String chat(@RequestParam("customerNumber") String number, @RequestParam("accountId") String account) {
        return number + account;
    }
}
