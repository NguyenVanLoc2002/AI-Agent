package com.secureerp.archfixture.conversation.adapter.web;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation (second review M4): every header as a map, so any customer header can be read. */
@RestController
public class HeaderMapController {

    public String chat(@RequestHeader Map<String, String> headers) {
        return headers.toString();
    }
}
