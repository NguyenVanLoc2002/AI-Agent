package com.secureerp.agent.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.platform.ErrorCode;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

class ErrorCodeMappingTest {

    @ParameterizedTest
    @CsvSource({
        "400,MALFORMED_REQUEST", "401,UNAUTHENTICATED", "403,FORBIDDEN", "404,NOT_FOUND",
        "405,METHOD_NOT_ALLOWED", "406,NOT_ACCEPTABLE", "409,CONFLICT", "413,PAYLOAD_TOO_LARGE",
        "415,UNSUPPORTED_MEDIA_TYPE", "429,TOO_MANY_REQUESTS", "418,REQUEST_FAILED", "503,SERVICE_UNAVAILABLE",
        "500,INTERNAL_ERROR", "502,INTERNAL_ERROR"
    })
    void statusMapsToStableCode(int status, ErrorCode expected) {
        assertThat(GlobalExceptionHandler.codeFor(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "'attrs[987654321]',attrs[]", "'lines[3].sku',lines[].sku", "'map[CUST-SYNTH-B].value',map[].value",
        "requestedQuantity,requestedQuantity"
    })
    void propertyPathsAreStrippedOfIndexAndKeyContent(String path, String expected) {
        assertThat(GlobalExceptionHandler.sanitizePath(path)).isEqualTo(expected);
    }

    @Test
    void causeChainContainsClassNamesOnly() {
        Exception ex = new IllegalStateException("debt=123456789",
                new UncheckedIOException("cost=987654321", new java.io.IOException("secret")));
        String chain = GlobalExceptionHandler.causeChain(ex);
        assertThat(chain).isEqualTo(
                "java.lang.IllegalStateException <- java.io.UncheckedIOException <- java.io.IOException");
    }

    @Test
    void ordinaryExceptionsAreNotTreatedAsSecurityExceptions() {
        assertThat(GlobalExceptionHandler.isSecurityException(new IllegalStateException())).isFalse();
        assertThat(GlobalExceptionHandler.SECURITY_EXCEPTIONS).contains(
                "org.springframework.security.access.AccessDeniedException",
                "org.springframework.security.core.AuthenticationException");
    }
}
