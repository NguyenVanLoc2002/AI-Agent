package com.secureerp.agent.gateway.api;

import com.secureerp.agent.platform.DataClassification;
import com.secureerp.agent.platform.EgressClassified;
import java.util.regex.Pattern;

/** A tool definition from the server-side registry; the model never defines tools (AI_GATEWAY §1). */
@EgressClassified(DataClassification.APPROVED_TECHNICAL)
public record ToolSpec(String name, String jsonSchema) {

    static final Pattern NAME = Pattern.compile("^[a-z][A-Za-z0-9]{2,40}$");

    public ToolSpec {
        EgressText.matching(name, "name", NAME);
        EgressText.text(jsonSchema, "jsonSchema", 4096);
    }
}
