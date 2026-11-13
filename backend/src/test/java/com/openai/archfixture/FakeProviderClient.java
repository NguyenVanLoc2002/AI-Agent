package com.openai.archfixture;

/** Stand-in for a provider SDK type (ARCH-01 fixture). Not a real SDK. */
public class FakeProviderClient {
    public String complete(String prompt) {
        return "";
    }
}
