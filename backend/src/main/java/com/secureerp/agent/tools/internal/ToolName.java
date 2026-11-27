package com.secureerp.agent.tools.internal;

import com.secureerp.agent.gateway.api.ToolSpec;
import com.secureerp.agent.identity.Channel;
import com.secureerp.agent.identity.Role;
import com.secureerp.agent.tools.ToolManager;
import java.util.Arrays;
import java.util.Optional;

/**
 * Static tool registry (TOOL_CONTRACTS §2, ADR-006; ARCH-11: no reflection). Three tools, offered
 * only to CUST on web chat. Anything else, including aliases and other letter case, is unknown.
 */
enum ToolName {

    SEARCH_VERIFIED_PRODUCT("searchVerifiedProduct", """
            {"type":"object","additionalProperties":false,"maxProperties":2,"properties":{\
            "vinRef":{"const":"current"},"oe":{"type":"string","pattern":"^[A-Z0-9][A-Z0-9-]{2,29}$"},\
            "category":{"type":"string","maxLength":80},\
            "vehicleModel":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9 .-]{0,49}$"}},\
            "oneOf":[{"required":["vinRef"]},{"required":["oe"]},{"required":["category","vehicleModel"]}]}"""),
    REQUEST_QUOTE(ToolManager.REQUEST_QUOTE, """
            {"type":"object","additionalProperties":false,"required":["lines"],"properties":{"lines":\
            {"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,\
            "required":["productRef","quantity"],"properties":{"productRef":{"type":"string"},\
            "quantity":{"type":"integer","minimum":1,"maximum":9999}}}}}}"""),
    REQUEST_HANDOFF("requestHandoff", """
            {"type":"object","additionalProperties":false,"required":["reason"],"properties":{"reason":\
            {"enum":["CUSTOMER_REQUEST","UNSURE","OUT_OF_SCOPE"]}}}""");

    private final String wireName;
    private final ToolSpec spec;

    ToolName(String wireName, String jsonSchema) {
        this.wireName = wireName;
        this.spec = new ToolSpec(wireName, jsonSchema);
    }

    String wireName() {
        return wireName;
    }

    ToolSpec spec() {
        return spec;
    }

    /** TOOL_CONTRACTS §2: staff roles do not use agent tools in the PoC. */
    boolean allows(Role role, Channel channel) {
        return role == Role.CUST && channel == Channel.WEB_CHAT;
    }

    /** Exact, case-sensitive match on the registered name; no aliases. */
    static Optional<ToolName> fromWire(String name) {
        return Arrays.stream(values()).filter(tool -> tool.wireName.equals(name)).findFirst();
    }
}
