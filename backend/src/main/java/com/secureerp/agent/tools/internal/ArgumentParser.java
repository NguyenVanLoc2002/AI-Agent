package com.secureerp.agent.tools.internal;

import com.secureerp.agent.gateway.api.EgressText;
import com.secureerp.agent.gateway.api.ProductRef;
import com.secureerp.agent.gateway.api.ToolErrorResult;
import com.secureerp.agent.tools.internal.ToolArguments.HandoffRequest;
import com.secureerp.agent.tools.internal.ToolArguments.QuoteLines;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict argument validation (TOOL_CONTRACTS §1, §3): a streaming pass enforces depth, array size
 * and string length before any tree is built (T-TOOL-03), then each tool's arguments must match
 * exactly one shape with no extra or missing keys, exact JSON types and bounded values (T-TOOL-02).
 * A full VIN in any string is rejected (T-TOOL-13). Every failure is {@code TOOL_SCHEMA}; the
 * offending value is never reported.
 */
final class ArgumentParser {

    /** Same first-character rule as {@code ExactProductQuery.ByOe}: a letter or digit first. */
    static final Pattern OE = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,29}$");
    static final Pattern CATEGORY = Pattern.compile("^\\p{L}+(?: \\p{L}+){0,15}$");
    static final Pattern VEHICLE_MODEL = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 .-]{0,49}$");
    static final int MAX_QUOTE_LINES = 20;
    static final int MAX_QUANTITY = 9999;

    /**
     * Any 17 consecutive VIN characters with a digit and a letter, also inside a longer token or
     * written with separators (EP-03 security review LOW-3: a boundary let an 18-character token
     * through). Longer codes are refused with it: fail closed.
     */
    private static final Pattern VIN = Pattern.compile(
            "(?=[A-HJ-NPR-Z0-9]{0,16}\\d)(?=[A-HJ-NPR-Z0-9]{0,16}[A-HJ-NPR-Z])[A-HJ-NPR-Z0-9]{17}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-_/]");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ArgumentParser() {
    }

    static ToolArguments parse(ToolName tool, String json, ToolLimits limits) {
        checkShape(json, limits);
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw schema();
        }
        if (root == null || !root.isObject()) {
            throw schema();
        }
        rejectFullVin(root);
        return switch (tool) {
            case SEARCH_VERIFIED_PRODUCT -> search(root);
            case REQUEST_QUOTE -> quote(root);
            case REQUEST_HANDOFF -> handoff(root);
        };
    }

    /** Streaming limits before the tree is built: depth, items per array, string length. */
    private static void checkShape(String json, ToolLimits limits) {
        Deque<int[]> containers = new ArrayDeque<>(); // {isArray ? items : -1}
        try (JsonParser parser = MAPPER.createParser(json)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                switch (token) {
                    case START_OBJECT, START_ARRAY -> {
                        countItem(containers, limits);
                        containers.push(new int[] {token == JsonToken.START_ARRAY ? 0 : -1});
                        if (containers.size() > limits.maxDepth()) {
                            throw schema();
                        }
                    }
                    case END_OBJECT, END_ARRAY -> containers.pop();
                    case PROPERTY_NAME -> {
                        if (parser.currentName().length() > limits.maxStringChars()) {
                            throw schema();
                        }
                    }
                    case VALUE_STRING -> {
                        countItem(containers, limits);
                        if (parser.getString().length() > limits.maxStringChars()) {
                            throw schema();
                        }
                    }
                    default -> countItem(containers, limits);
                }
            }
        } catch (JacksonException e) {
            throw schema();
        }
    }

    private static void countItem(Deque<int[]> containers, ToolLimits limits) {
        int[] parent = containers.peek();
        if (parent != null && parent[0] >= 0 && ++parent[0] > limits.maxArrayItems()) {
            throw schema();
        }
    }

    /** True if the value contains a full VIN, plain or with separators, in any letter case. */
    static boolean containsFullVin(String value) {
        if (value == null) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return VIN.matcher(upper).find() || VIN.matcher(SEPARATORS.matcher(upper).replaceAll("")).find();
    }

    private static void rejectFullVin(JsonNode node) {
        if (node.isString()) {
            if (containsFullVin(node.asString())) {
                throw schema();
            }
            return;
        }
        for (JsonNode child : node) {
            rejectFullVin(child);
        }
    }

    private static ToolArguments search(JsonNode root) {
        Set<String> keys = keys(root);
        if (keys.equals(Set.of("vinRef"))) {
            if (!"current".equals(string(root, "vinRef"))) {
                throw schema();
            }
            return new ToolArguments.SearchByVinRef();
        }
        if (keys.equals(Set.of("oe"))) {
            return new ToolArguments.SearchByOe(matching(string(root, "oe"), OE));
        }
        if (keys.equals(Set.of("category", "vehicleModel"))) {
            String category = string(root, "category");
            if (category.length() > 80) {
                throw schema();
            }
            return new ToolArguments.SearchByCategoryAndModel(matching(category, CATEGORY),
                    matching(string(root, "vehicleModel"), VEHICLE_MODEL));
        }
        throw schema();
    }

    private static ToolArguments quote(JsonNode root) {
        if (!keys(root).equals(Set.of("lines"))) {
            throw schema();
        }
        JsonNode lines = root.get("lines");
        if (!lines.isArray() || lines.isEmpty() || lines.size() > MAX_QUOTE_LINES) {
            throw schema();
        }
        List<QuoteLines.Line> parsed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode line : lines) {
            if (!line.isObject() || !keys(line).equals(Set.of("productRef", "quantity"))) {
                throw schema();
            }
            String productRef = string(line, "productRef");
            try {
                EgressText.serverToken(productRef, "productRef", ProductRef.PRODUCT_REF_PREFIX);
            } catch (IllegalArgumentException e) {
                throw schema();
            }
            JsonNode quantity = line.get("quantity");
            if (!quantity.isInt() || quantity.intValue() < 1 || quantity.intValue() > MAX_QUANTITY
                    || !seen.add(productRef)) {
                throw schema();
            }
            parsed.add(new QuoteLines.Line(productRef, quantity.intValue()));
        }
        return new QuoteLines(parsed);
    }

    private static ToolArguments handoff(JsonNode root) {
        if (!keys(root).equals(Set.of("reason"))) {
            throw schema();
        }
        String reason = string(root, "reason");
        for (HandoffRequest.Reason candidate : HandoffRequest.Reason.values()) {
            if (candidate.name().equals(reason)) {
                return new HandoffRequest(candidate);
            }
        }
        throw schema();
    }

    private static Set<String> keys(JsonNode object) {
        Set<String> keys = new HashSet<>();
        Iterator<String> names = object.propertyNames().iterator();
        names.forEachRemaining(keys::add);
        return keys;
    }

    /** A JSON string exactly; numbers, booleans and nulls are not coerced. */
    private static String string(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isString()) {
            throw schema();
        }
        return value.asString();
    }

    private static String matching(String value, Pattern pattern) {
        if (!pattern.matcher(value).matches()) {
            throw schema();
        }
        return value;
    }

    private static ToolRejection schema() {
        return ToolRejection.of(ToolErrorResult.Code.TOOL_SCHEMA);
    }
}
