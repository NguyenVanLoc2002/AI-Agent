package com.secureerp.archfixture.conversation.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-08b violation (second review M4): raw request access bypasses parameter checks. */
@RestController
public class ServletRequestController {

    public String chat(HttpServletRequest request) {
        return request.getHeader("X-Customer-Id");
    }
}
