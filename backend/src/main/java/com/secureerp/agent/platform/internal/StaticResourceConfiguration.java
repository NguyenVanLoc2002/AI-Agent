package com.secureerp.agent.platform.internal;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * FE-DEPLOY (A-74, decision 1A): the backend serves the built frontend from {@code classpath:/static/} (copied into
 * the jar by the Maven profile {@code with-frontend}). Spring Boot's default mapping of every static file is off
 * ({@code spring.web.resources.add-mappings: false}, review security L2): only {@code /} (forwarded to
 * {@code /index.html}), {@code /index.html} and {@code /assets/**} exist, so no other file under {@code static/}
 * is ever served, with or without a session.
 *
 * <p>The bundle's files under {@code /assets/} carry a content hash in their names, so they are cached for a year
 * and never revalidated (3A). {@code index.html} has no cache rule here: it keeps Spring Security's
 * {@code no-store}, so a new release is picked up at once. The prefix {@code /assets/} must match Vite's
 * {@code build.assetsDir} ({@code frontend/vite.config.ts}) and {@code identity.FrontendPaths}, which decides what
 * is reachable without a session.
 */
@Configuration(proxyBeanMethods = false)
class StaticResourceConfiguration implements WebMvcConfigurer {

    static final Duration ASSET_MAX_AGE = Duration.ofDays(365);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(ASSET_MAX_AGE).cachePublic().immutable());
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
