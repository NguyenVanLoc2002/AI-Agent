package com.secureerp.agent.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Error/validation contract (POC-001 req. 4): stable code, RFC 9457 shape, trace ID, and no
 * leakage of exception messages, rejected values or stack traces in responses or logs.
 */
@WebMvcTest(controllers = ValidationProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String SENTINEL = ValidationProbeController.SECRET_IN_MESSAGE;

    @Autowired
    MockMvc mvc;

    @Test
    void validRequestPasses() throws Exception {
        mvc.perform(post("/test-probe/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-SYNTH-1\",\"requestedQuantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void invalidBodyReturnsFieldNamesOnlyWithoutRejectedValues() throws Exception {
        MvcResult result = mvc.perform(post("/test-probe/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"requestedQuantity\":" + SENTINEL + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fields[0].field").value("requestedQuantity"))
                .andExpect(jsonPath("$.fields[0].constraint").value("Max"))
                .andExpect(jsonPath("$.fields[1].field").value("sku"))
                .andExpect(jsonPath("$.fields[1].constraint").value("NotBlank"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENTINEL).doesNotContain("must");
    }

    @Test
    void unknownJsonPropertyIsRejected() throws Exception {
        mvc.perform(post("/test-probe/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-SYNTH-1\",\"requestedQuantity\":1,\"customerId\":\"CUST-SYNTH-B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void malformedJsonIsRejectedWithoutEcho() throws Exception {
        MvcResult result = mvc.perform(post("/test-probe/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": " + SENTINEL + "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENTINEL);
    }

    @Test
    void invalidRequestParameterIsValidationFailure() throws Exception {
        mvc.perform(get("/test-probe/count").param("limit", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].field").value("limit"));
    }

    @Test
    void typeMismatchIsMalformed() throws Exception {
        mvc.perform(get("/test-probe/count").param("limit", "abc" + SENTINEL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsupportedMediaTypeAndMethod() throws Exception {
        mvc.perform(post("/test-probe/items").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post("/test-probe/count"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/test-probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unexpectedExceptionLeaksNothing(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get("/test-probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(SENTINEL).doesNotContain("CUST-SYNTH").doesNotContain("IllegalState")
                .doesNotContain("at com.");
        assertThat(output.getAll()).contains("INTERNAL_ERROR").doesNotContain(SENTINEL).doesNotContain("CUST-SYNTH");
    }

    @Test
    void applicationExceptionMapsToItsCode(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get("/test-probe/app-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENTINEL);
        assertThat(output.getAll()).doesNotContain(SENTINEL);
    }

    @Test
    void traceIdIsAlwaysServerGenerated() throws Exception {
        MvcResult result = mvc.perform(get("/test-probe/count").param("limit", "1")
                        .header(CorrelationIdFilter.HEADER, "abc-12345678"))
                .andReturn();
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER))
                .isNotEqualTo("abc-12345678")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHENTICATED", "403,FORBIDDEN", "409,CONFLICT", "429,TOO_MANY_REQUESTS",
        "418,REQUEST_FAILED", "502,INTERNAL_ERROR"})
    void responseStatusExceptionKeepsItsStatus(int status, String code) throws Exception {
        MvcResult result = mvc.perform(get("/test-probe/status/" + status))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENTINEL).doesNotContain("reason");
    }

    @Test
    void mapKeysInValidationPathsAreNotEchoed() throws Exception {
        MvcResult result = mvc.perform(post("/test-probe/attributes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-SYNTH-1\",\"attrs\":{\"" + SENTINEL + "\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENTINEL).contains("attrs[]");
    }

    @Test
    void classLevelConstraintIsReported() throws Exception {
        mvc.perform(post("/test-probe/range").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":5,\"to\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("(object)"))
                .andExpect(jsonPath("$.fields[0].constraint").value("ValidRange"));
    }
}
