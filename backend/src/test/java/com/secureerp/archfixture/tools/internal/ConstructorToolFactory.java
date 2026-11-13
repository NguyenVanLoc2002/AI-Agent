package com.secureerp.archfixture.tools.internal;

/** Violation ARCH-11: reflective construction of tools. */
public class ConstructorToolFactory {
    public Object create(Class<?> type) throws Exception {
        return type.getDeclaredConstructor().newInstance();
    }
}
