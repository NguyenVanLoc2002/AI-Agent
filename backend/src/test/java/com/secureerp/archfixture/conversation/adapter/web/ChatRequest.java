package com.secureerp.archfixture.conversation.adapter.web;

import com.secureerp.archfixture.identity.AuthenticatedCustomerRef;

/** ARCH-08c violation: request body nests the customer reference. */
public record ChatRequest(AuthenticatedCustomerRef customer, String text) {
}
