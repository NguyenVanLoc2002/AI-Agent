package com.secureerp.archfixture.tools.internal;

import java.lang.reflect.Method;

/** Violation ARCH-11: reflection-based tool invocation. */
public class ReflectiveToolInvoker {
    public Object invoke(Object tool, String name) throws Exception {
        Method method = tool.getClass().getMethod(name);
        return method.invoke(tool);
    }
}
