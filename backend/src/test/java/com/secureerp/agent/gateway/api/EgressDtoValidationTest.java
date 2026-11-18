package com.secureerp.agent.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.secureerp.agent.gateway.EgressFixtures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.function.Executable;

/**
 * T-SEC01-SCHEMA (unit part): egress records reject out-of-allowlist shapes at construction, and the
 * error message names the field but never echoes the rejected value.
 */
class EgressDtoValidationTest {

    private static final String SENTINEL_FREE_MARKER = "REJECTED-VALUE-MARKER";

    @Test
    void validFixtureIsAccepted() {
        assertThatCode(() -> EgressFixtures.request("Toi can ma phanh cho Vios 2019")).doesNotThrowAnyException();
    }

    static Stream<Arguments> invalidShapes() {
        SafeMessage ok = new SafeMessage(SafeMessage.Role.CUSTOMER, "xin chao");
        return Stream.of(
                Arguments.of("traceId not a UUID", (Executable) () -> EgressFixtures.request(
                        "trace-" + SENTINEL_FREE_MARKER, List.of(ok))),
                Arguments.of("no messages", (Executable) () -> EgressFixtures.request(EgressFixtures.TRACE_ID, List.of())),
                Arguments.of("too many messages", (Executable) () -> EgressFixtures.request(EgressFixtures.TRACE_ID,
                        Collections.nCopies(ModelRequest.MAX_MESSAGES + 1, ok))),
                Arguments.of("null message element", (Executable) () -> EgressFixtures.request(EgressFixtures.TRACE_ID,
                        nullElementList(ok))),
                Arguments.of("oversized text", (Executable) () -> new SafeMessage(SafeMessage.Role.CUSTOMER,
                        "a".repeat(SafeMessage.MAX_TEXT_CHARS + 1))),
                Arguments.of("bidi override in text", (Executable) () -> new SafeMessage(SafeMessage.Role.CUSTOMER,
                        "gia ‮" + SENTINEL_FREE_MARKER)),
                Arguments.of("control char in text", (Executable) () -> new SafeMessage(SafeMessage.Role.CUSTOMER,
                        "a\u0000" + SENTINEL_FREE_MARKER)),
                Arguments.of("full VIN in maskedVin", (Executable) () -> new TechnicalContext("JTDBT923X71234567",
                        null, null, null, null, null, null)),
                Arguments.of("quantity zero", (Executable) () -> new TechnicalContext(null, null, null, null, 0,
                        null, null)),
                Arguments.of("quantity too large", (Executable) () -> new TechnicalContext(null, null, null, null,
                        TechnicalContext.MAX_QUANTITY + 1, null, null)),
                Arguments.of("malformed quote handle", (Executable) () -> new TechnicalContext(null, null, null, null,
                        null, null, "quote-42")),
                Arguments.of("lowercase SKU injection", (Executable) () -> new ProductRef("pr_SynthProd01AAAAAAAAAAA",
                        "sku; drop", null, "Ma phanh", Provenance.VERIFIED, AvailabilityBucket.CON_HANG)),
                Arguments.of("digits in category label", (Executable) () -> new ProductRef("pr_SynthProd01AAAAAAAAAAA",
                        "SKU-BRK-PAD-A", null, "Ma phanh " + SENTINEL_FREE_MARKER + " 1250000", Provenance.VERIFIED,
                        AvailabilityBucket.CON_HANG)),
                Arguments.of("punctuation in part category", (Executable) () -> new TechnicalContext(null, null, null,
                        "Ma phanh - KH " + SENTINEL_FREE_MARKER, null, null, null)),
                Arguments.of("too many candidates", (Executable) () -> new ProductSearchResult(
                        Collections.nCopies(ProductSearchResult.MAX_CANDIDATES + 1, EgressFixtures.product()))),
                Arguments.of("NOT_QUOTED with handle", (Executable) () -> new QuoteRequestResult(
                        QuoteRequestResult.Status.NOT_QUOTED, EgressFixtures.QUOTE_HANDLE, 0)),
                Arguments.of("ISSUED without handle", (Executable) () -> new QuoteRequestResult(
                        QuoteRequestResult.Status.ISSUED, null, 1)),
                Arguments.of("too many lines", (Executable) () -> new QuoteRequestResult(
                        QuoteRequestResult.Status.ISSUED, EgressFixtures.QUOTE_HANDLE, QuoteRequestResult.MAX_LINES + 1)),
                Arguments.of("tool name with path", (Executable) () -> new ToolSpec("../approveOrder", "{}")),
                Arguments.of("oversized tool arguments", (Executable) () -> new ToolProposal("searchVerifiedProduct",
                        "x".repeat(ToolProposal.MAX_ARGUMENT_BYTES + 1))),
                Arguments.of("empty model response", (Executable) () -> new ModelResponse(null, List.of())),
                Arguments.of("too many proposals", (Executable) () -> new ModelResponse(null, Collections.nCopies(
                        ModelResponse.MAX_PROPOSALS + 1, new ToolProposal("searchVerifiedProduct", "{}")))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidShapes")
    void invalidShapeIsRejectedWithoutEchoingValue(String name, Executable construction) {
        assertThatThrownBy(construction::execute)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage()))
                        .doesNotContain(SENTINEL_FREE_MARKER)
                        .doesNotContain("JTDBT923X71234567"));
    }

    @Test
    void listsAreDefensivelyCopied() {
        List<SafeMessage> messages = new ArrayList<>(List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "a")));
        ModelRequest request = EgressFixtures.request(EgressFixtures.TRACE_ID, messages);
        messages.add(new SafeMessage(SafeMessage.Role.CUSTOMER, "added later"));
        assertThat(request.messages()).hasSize(1);
        assertThatThrownBy(() -> request.messages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<SafeMessage> nullElementList(SafeMessage ok) {
        List<SafeMessage> list = new ArrayList<>();
        list.add(ok);
        list.add(null);
        return list;
    }

    /**
     * Second review M4: the opaque-ref fields get the honeytoken check only, so their format must
     * not fit a business value: exactly 22 token characters, no run of 7+ digits.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"vin_JTDBT923X71234567", "vin_JTDBT923X71234567AAAAA", "vin_AAAAAAAAAAAA0912345678",
        "vin_AAAAAAAAAAAAAAAAAAAAA", "vin_AAAAAAAAAAAAAAAAAAAAAAA", "vin_AAAAAAAAAAAAAAAAAAAA!A"})
    void vinRefThatCouldCarryAValueIsRejected(String vinRef) {
        assertThatThrownBy(() -> new TechnicalContext(null, vinRef, null, null, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productRefAndQuoteHandleRejectDerivedValues() {
        assertThatThrownBy(() -> new ProductRef("pr_SKUBRKPADA", "SKU-BRK-PAD-A", null, "Ma phanh",
                Provenance.VERIFIED, AvailabilityBucket.CON_HANG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductRef("pr_AAAAAAAAAAAA0912345678", "SKU-BRK-PAD-A", null, "Ma phanh",
                Provenance.VERIFIED, AvailabilityBucket.CON_HANG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteRequestResult(QuoteRequestResult.Status.ISSUED, "qh_987654321AAAAAAAAAAAAA", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new TechnicalContext(null, "vin_Ab3-_x9ZqLm0Pw1Rt5Yu7K", null, null, null, List.of(),
                "qh_Ab3-_x9ZqLm0Pw1Rt5Yu7K")).doesNotThrowAnyException();
    }

    @Test
    void promptIdCannotCarryADigitRun() {
        assertThatCode(() -> new ModelRequest(EgressFixtures.TRACE_ID, "apas.chat.v1",
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "xin chao")), null, List.of(),
                List.of(), EgressFixtures.request("xin chao").budget())).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ModelRequest(EgressFixtures.TRACE_ID, "p0912345678",
                List.of(new SafeMessage(SafeMessage.Role.CUSTOMER, "xin chao")), null, List.of(),
                List.of(), EgressFixtures.request("xin chao").budget())).isInstanceOf(IllegalArgumentException.class);
    }
}
