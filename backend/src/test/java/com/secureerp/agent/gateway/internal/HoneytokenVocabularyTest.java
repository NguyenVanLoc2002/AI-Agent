package com.secureerp.agent.gateway.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.secureerp.agent.gateway.api.AvailabilityBucket;
import com.secureerp.agent.gateway.api.Provenance;
import com.secureerp.agent.gateway.api.QuoteRequestResult;
import com.secureerp.agent.gateway.api.ResponseBudget;
import com.secureerp.agent.gateway.api.SafeMessage;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.policy.internal.Honeytoken;
import com.secureerp.agent.policy.internal.SensitiveDataDetector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * SEC03-F4-MORE (A-60): the fixed vocabulary of a provider request (property names, enum values, tool names, handoff
 * reasons, the prompt ID) never assembles a registered honeytoken across values, alone or with ordinary parts-chat
 * text. The split check (A-57) joins values order-free, so a token that the vocabulary could form would block every
 * request; this test runs whenever the registry or the request types change.
 */
class HoneytokenVocabularyTest {

    /** Tool names and handoff reasons of TOOL_CONTRACTS; the prompt ID of {@code DefaultAgent}. */
    private static final List<String> TOOL_VOCABULARY = List.of("searchVerifiedProduct", "requestQuote",
            "requestHandoff", "CUSTOMER_REQUEST", "UNSURE", "OUT_OF_SCOPE", "apas.chat.v1", "current");

    /** Ordinary customer and catalog text of a parts chat (synthetic). */
    private static final List<String> CHAT = List.of("Xin chào, tôi cần má phanh trước cho Vios 2019",
            "Người nhận là anh Nguyen, giao trong 3 ngày", "Van An nhờ hỏi giá lọc gió Innova 2020",
            "Mã OE 04465-0K290 còn hàng không?", "Tôi muốn năm bộ, không phải ba", "Ma phanh truoc", "Loc gio dong co",
            "SKU-BRK-PAD-A", "17801-0Y040", "Cảm ơn, khách hàng sẽ ghé cửa hàng", "Customer service ở đâu?",
            "Honda City hay Toyota Vios?", "Email của tôi là khach@example.test");

    static List<String> vocabulary() {
        List<String> words = new ArrayList<>(EgressSerializer.ALLOWED_PROPERTY_NAMES);
        Stream.of(SafeMessage.Role.values(), Provenance.values(), AvailabilityBucket.values(), ResponseBudget.values(),
                        ToolErrorResult.Code.values(), QuoteRequestResult.Status.values())
                .flatMap(Arrays::stream).map(Enum::name).forEach(words::add);
        words.addAll(TOOL_VOCABULARY);
        return words;
    }

    @Test
    void theFixedVocabularyAssemblesNoRegisteredHoneytoken() {
        SensitiveDataDetector detector = new SensitiveDataDetector();

        assertThat(vocabulary()).hasSizeGreaterThan(30);
        assertThat(detector.splitHoneytoken(vocabulary())).as("vocabulary alone").isEmpty();
        List<String> withChat = new ArrayList<>(vocabulary());
        withChat.addAll(CHAT);
        assertThat(detector.splitHoneytoken(withChat)).as("vocabulary and ordinary chat").isEmpty();
    }

    /**
     * A-60 measurement for a future real registry: a name-like text token ("Nguyen Van A") is formed neither by the
     * fixed vocabulary nor by the ordinary chat above, where the name parts sit inside longer sentences. It is formed
     * when one value ends exactly with "Nguyen" and another starts exactly with "Van A" (recorded as a residual in
     * A-60: a real registry needs distinctive tokens, not plain personal names).
     */
    @Test
    void aNameLikeTokenIsFormedOnlyByValuesCutAtTheNameBoundary() {
        SensitiveDataDetector nameLike = new SensitiveDataDetector(
                List.of(new Honeytoken("HT-NAME-X", Honeytoken.Kind.TEXT, "Nguyen Van A")));
        List<String> withChat = new ArrayList<>(vocabulary());
        withChat.addAll(CHAT);

        assertThat(nameLike.splitHoneytoken(vocabulary())).as("vocabulary alone").isEmpty();
        assertThat(nameLike.splitHoneytoken(withChat)).as("vocabulary and ordinary chat").isEmpty();
        assertThat(nameLike.splitHoneytoken(List.of("Người nhận là anh Nguyen", "Van An"))).as("cut at the name")
                .isPresent();
    }
}
