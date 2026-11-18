package com.secureerp.archfixture.conversation.adapter.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.RestController;

/** Custom stereotype meta-annotated with @RestController. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@RestController
public @interface ApiController {
}
