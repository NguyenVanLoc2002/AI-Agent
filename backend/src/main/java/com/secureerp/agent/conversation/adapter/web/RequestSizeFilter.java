package com.secureerp.agent.conversation.adapter.web;

import com.secureerp.agent.platform.ErrorCode;
import com.secureerp.agent.platform.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * T-HTTP-01: a chat request body is limited before any parsing. A body without a declared length
 * (chunked) or over the limit gets 413 with the sanitized problem shape. Runs after the security chain
 * (authentication and CSRF first). Profile {@code demo} only, like the endpoints (ADR-015).
 */
class RequestSizeFilter extends OncePerRequestFilter {

    private final long maxBytes;

    RequestSizeFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        long length = request.getContentLengthLong();
        if (hasBody && (length > maxBytes || (length < 0 && request.getHeader("Transfer-Encoding") != null))) {
            ProblemResponses.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("demo")
    static class Registration {

        @Bean
        FilterRegistrationBean<RequestSizeFilter> chatRequestSizeFilter(
                @Value("${apas.conversation.max-request-bytes}") long maxRequestBytes) {
            FilterRegistrationBean<RequestSizeFilter> registration =
                    new FilterRegistrationBean<>(new RequestSizeFilter(maxRequestBytes));
            // EP-07: the staff reconciliation endpoint (order) takes a small JSON body as well.
            registration.addUrlPatterns("/api/demo/*", "/api/internal/*");
            return registration;
        }
    }
}
