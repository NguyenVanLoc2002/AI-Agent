package com.secureerp.archfixture.conversation.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Request bodies whose Java names look harmless but whose wire names carry customer identity. */
public final class RenamedBodies {

    private RenamedBodies() {
    }

    public record ByProperty(@JsonProperty("customerId") String owner, String text) {
    }

    public record ByAlias(@JsonAlias({"clientRef", "buyerId"}) String owner, String text) {
    }
}
