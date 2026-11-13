package com.secureerp.archfixture.tools.internal;

import java.util.function.Function;

/** Compliant ARCH-11: typed invocation. */
public class TypedToolInvoker {
    public <I, O> O invoke(Function<I, O> tool, I input) {
        return tool.apply(input);
    }
}
