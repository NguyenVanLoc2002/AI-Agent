package com.secureerp.archfixture.conversation.adapter.web;

/** ARCH-08c violation: request body carries a client-supplied customer ID. */
public record ChatRequestWithId(String customerId, String text) {
}
