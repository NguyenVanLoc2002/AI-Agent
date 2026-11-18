package com.secureerp.agent.tools.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.tools.internal.ToolArguments.HandoffRequest;
import com.secureerp.agent.tools.internal.ToolArguments.QuoteLines;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByCategoryAndModel;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByOe;
import com.secureerp.agent.tools.internal.ToolArguments.SearchByVinRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Strict schema and limits (TOOL_CONTRACTS §1, §3; T-TOOL-02, T-TOOL-03, T-TOOL-13). */
class ArgumentParserTest {

    private static final ToolLimits LIMITS = ToolLimits.DEFAULTS;
    private static final String REF = "pr_Ab3-_x9ZqLm0Pw1Rt5Yu7K";

    private static ToolArguments parse(ToolName tool, String json) {
        return ArgumentParser.parse(tool, json, LIMITS);
    }

    private static void assertSchemaRejected(ToolName tool, String json) {
        assertThatThrownBy(() -> parse(tool, json)).isInstanceOf(ToolRejection.class)
                .satisfies(e -> assertThat(((ToolRejection) e).code()).isEqualTo(ToolErrorResult.Code.TOOL_SCHEMA))
                .hasMessage("TOOL_SCHEMA");
    }

    @Test
    void validShapesAreParsedIntoTypedArguments() {
        assertThat(parse(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"oe\":\"04465-0K290\"}"))
                .isEqualTo(new SearchByOe("04465-0K290"));
        assertThat(parse(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"vinRef\":\"current\"}"))
                .isEqualTo(new SearchByVinRef());
        assertThat(parse(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"category\":\"Ma phanh truoc\",\"vehicleModel\":\"Vios 2019\"}"))
                .isEqualTo(new SearchByCategoryAndModel("Ma phanh truoc", "Vios 2019"));
        assertThat(parse(ToolName.REQUEST_QUOTE, "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2}]}"))
                .isEqualTo(new QuoteLines(List.of(new QuoteLines.Line(REF, 2))));
        assertThat(parse(ToolName.REQUEST_HANDOFF, "{\"reason\":\"UNSURE\"}"))
                .isEqualTo(new HandoffRequest(HandoffRequest.Reason.UNSURE));
    }

    /** T-TOOL-02: identity and extra fields, top level and nested, are schema errors. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"oe\":\"04465-0K290\",\"customerId\":\"C001\"}",
        "{\"oe\":\"04465-0K290\",\"tenantId\":\"T1\"}",
        "{\"oe\":\"04465-0K290\",\"price\":1}",
        "{\"oe\":\"04465-0K290\",\"vinRef\":\"current\"}",
        "{\"category\":\"Ma phanh\"}",
        "{\"oe\":{\"customerId\":\"C001\"}}",
        "{}",
        "[]",
        "\"oe\"",
        "{\"oe\":\"04465-0k290\"}",
        "{\"oe\":\"04465 0K290; drop table\"}",
        "{\"oe\":123456}",
        "{\"oe\":null}",
        "{\"vinRef\":\"previous\"}",
        "{\"category\":\"Ma phanh 2\",\"vehicleModel\":\"Vios\"}",
        "{\"category\":\"Ma phanh\",\"vehicleModel\":\"Vios<script>\"}",
        "{\"oe\":\"04465-0K290\",\"oe\":\"90915-YZZD4\"}",
        "{\"oe\":\"04465-0K290\"} trailing"
    })
    void searchRejectsAnythingButTheThreeShapes(String json) {
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, json);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"lines\":[]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":0}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":10000}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2.5}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":\"2\"}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2,\"price\":1}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2,\"customerId\":\"C001\"}]}",
        "{\"lines\":[{\"productRef\":\"pr_SKUBRKPADA\",\"quantity\":2}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2},{\"productRef\":\"" + REF + "\",\"quantity\":1}]}",
        "{\"lines\":[{\"productRef\":\"" + REF + "\",\"quantity\":2}],\"discount\":10}"
    })
    void quoteRejectsBadLines(String json) {
        assertSchemaRejected(ToolName.REQUEST_QUOTE, json);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"reason\":\"APPROVE_ORDER\"}", "{\"reason\":\"unsure\"}", "{\"reason\":1}",
        "{\"reason\":\"UNSURE\",\"note\":\"x\"}"})
    void handoffRejectsUnknownReasons(String json) {
        assertSchemaRejected(ToolName.REQUEST_HANDOFF, json);
    }

    /** T-TOOL-03: depth, array size and string length are checked before a tree is built. */
    @Test
    void oversizedShapesAreRejected() {
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"oe\":" + "[".repeat(50) + "1" + "]".repeat(50) + "}");
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"a\":{\"b\":{\"c\":{\"d\":1}}}}");
        StringBuilder lines = new StringBuilder("{\"lines\":[");
        for (int i = 0; i < 21; i++) {
            lines.append(i == 0 ? "" : ",").append("1");
        }
        assertSchemaRejected(ToolName.REQUEST_QUOTE, lines.append("]}").toString());
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"oe\":\"" + "A".repeat(300) + "\"}");
    }

    /** T-TOOL-13: a full VIN in any argument, plain or with separators. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "{\"oe\":\"JTDBT923X71234567\"}",
        "{\"oe\":\"JTDBT-923X7-1234567\"}",
        "{\"category\":\"Ma phanh\",\"vehicleModel\":\"Vios JTDBT923X71234567\"}",
        "{\"category\":\"Ma phanh\",\"vehicleModel\":\"jtdbt923x71234567\"}"
    })
    void fullVinInAnyArgumentIsRejected(String json) {
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, json);
    }

    /** EP-03 security review LOW-3: a VIN inside a longer token is still a VIN. */
    @Test
    void fullVinInsideALongerTokenIsRejected() {
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT,
                "{\"category\":\"Ma phanh\",\"vehicleModel\":\"AWVWZZZ1JZ3W386752\"}");
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, "{\"oe\":\"XJTDBT923X71234567\"}");
    }

    /** Architecture review 2, LOW-3: values the catalog query would refuse are schema errors here. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"oe\":\"-AB\"}", "{\"oe\":\"--0K290\"}",
        "{\"category\":\"Ma phanh\",\"vehicleModel\":\" \"}",
        "{\"category\":\"Ma phanh\",\"vehicleModel\":\" Vios\"}"})
    void valuesTheCatalogQueryRefusesAreSchemaErrors(String json) {
        assertSchemaRejected(ToolName.SEARCH_VERIFIED_PRODUCT, json);
    }
}
