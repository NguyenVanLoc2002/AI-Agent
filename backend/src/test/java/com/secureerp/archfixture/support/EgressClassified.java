package com.secureerp.archfixture.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Lookalike with the same simple name as the real marker (review L1). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EgressClassified {

    DataClassification value();
}
