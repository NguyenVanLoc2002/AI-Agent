package com.secureerp.archfixture.conversation.adapter.web;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violations (second review M4): names the old regex missed, one per method. */
@RestController
public class CustomerNumberController {

    public String chatByNumber(@RequestParam("customerNumber") String number) {
        return number;
    }

    public String chatByAccount(@RequestParam("accountId") String account) {
        return account;
    }
}
