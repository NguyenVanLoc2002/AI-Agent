package com.secureerp.agent.gateway.internal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.secureerp.agent.gateway.api.ModelRequest;
import com.secureerp.agent.gateway.api.ModelResponse;
import com.secureerp.agent.gateway.api.ToolResultDto;
import com.secureerp.agent.policy.EgressPayload;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway-private JSON handling (AI_GATEWAY §2.2): its own mappers, independent of Spring's.
 *
 * <ul>
 *   <li>Outbound: only record fields are serialized (no getters, no any-getters), nulls omitted, no
 *       default typing; the subtype discriminator is the fixed name in {@link ToolResultDto}.</li>
 *   <li>Structure check: the produced bytes are parsed again and every property name must be in the
 *       allowlist derived from the {@code gateway.api} record components ("reject on unclassified
 *       fields"). This catches serializer drift, not just DTO changes.</li>
 *   <li>Inbound: provider answers are parsed strictly (unknown properties, trailing tokens and
 *       duplicate keys fail).</li>
 * </ul>
 */
public final class EgressSerializer {

    /** Upper bound of a provider answer body (DESIGN PROPOSAL, SEC-01a). */
    public static final int MAX_RESPONSE_BYTES = 32 * 1024;

    private static final int MAX_DEPTH = 16;

    private static final JsonMapper OUTBOUND = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
            .changeDefaultPropertyInclusion(i -> i.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private static final JsonMapper STRUCTURE = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final JsonMapper INBOUND = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /** Property names allowed in an outbound payload: all egress record components + "type". */
    static final Set<String> ALLOWED_PROPERTY_NAMES = allowedNames();

    private EgressSerializer() {
    }

    public static byte[] serialize(ModelRequest request) {
        return OUTBOUND.writeValueAsBytes(request);
    }

    /**
     * Parses the outbound bytes into the policy's view. Empty if the structure is not an object, is
     * too deep, or contains a property outside the allowlist.
     */
    public static Optional<Structure> structure(byte[] body) {
        try {
            JsonNode root = STRUCTURE.readTree(body);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            List<EgressPayload.Segment> segments = new ArrayList<>();
            Set<String> names = new TreeSet<>();
            if (!walk(root, "$", 0, segments, names)) {
                return Optional.empty();
            }
            return Optional.of(new Structure(new EgressPayload(new String(body, StandardCharsets.UTF_8), segments),
                    List.copyOf(names)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Strict parse of a provider answer; any problem is an invalid response. */
    public static Optional<ModelResponse> readResponse(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            return Optional.empty();
        }
        try {
            return Optional.of(INBOUND.readValue(body, ModelResponse.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** @param fieldNames distinct property names, sorted; used for the audit record */
    public record Structure(EgressPayload payload, List<String> fieldNames) {
    }

    private static boolean walk(JsonNode node, String path, int depth, List<EgressPayload.Segment> segments,
            Set<String> names) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                if (!ALLOWED_PROPERTY_NAMES.contains(property.getKey())) {
                    return false;
                }
                names.add(property.getKey());
                if (!walk(property.getValue(), path + "." + property.getKey(), depth + 1, segments, names)) {
                    return false;
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                if (!walk(node.get(i), path + "[" + i + "]", depth + 1, segments, names)) {
                    return false;
                }
            }
        } else if (node.isString()) {
            segments.add(new EgressPayload.Segment(path, node.stringValue()));
        }
        return true;
    }

    private static Set<String> allowedNames() {
        Set<String> names = new TreeSet<>();
        names.add("type");
        collect(ModelRequest.class, names, new HashSet<>());
        return Collections.unmodifiableSet(names);
    }

    private static void collect(Class<?> type, Set<String> names, Set<Class<?>> seen) {
        if (!seen.add(type) || !type.getPackageName().equals(ModelRequest.class.getPackageName())) {
            return;
        }
        if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                collect(permitted, names, seen);
            }
        }
        if (!type.isRecord()) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
            collectType(component.getGenericType(), names, seen);
        }
    }

    /** Follows nested generic arguments, consistent with ARCH-05 (architecture review L7). */
    private static void collectType(Type type, Set<String> names, Set<Class<?>> seen) {
        if (type instanceof Class<?> c) {
            collect(c, names, seen);
        } else if (type instanceof ParameterizedType parameterized) {
            collectType(parameterized.getRawType(), names, seen);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectType(argument, names, seen);
            }
        }
    }
}
