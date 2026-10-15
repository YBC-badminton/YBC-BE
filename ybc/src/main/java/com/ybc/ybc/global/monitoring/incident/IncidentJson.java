package com.ybc.ybc.global.monitoring.incident;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

final class IncidentJson {
    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static final int MAX_FILE_BYTES = 256 * 1024;

    private IncidentJson() { }

    static JsonNode parse(String text) {
        try {
            JsonNode value = MAPPER.readTree(text);
            require(value != null, "JSON이 비어 있습니다.");
            return value;
        } catch (IOException e) {
            // Jackson errors may contain excerpts of operational data.
            throw new IllegalArgumentException("JSON 형식이 올바르지 않습니다.");
        }
    }

    static String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank() && value.asText().length() <= maxLength,
                field + ": 비어 있지 않은 문자열이 필요합니다 (최대 " + maxLength + "자).");
        return value.asText();
    }

    static void fields(JsonNode node, Set<String> allowed) {
        require(node.isObject(), "JSON 객체가 필요합니다.");
        node.fieldNames().forEachRemaining(key -> require(allowed.contains(key), "허용되지 않은 JSON 필드가 있습니다."));
    }

    // Validate the same small JSON Schema subset used by the Structured Outputs contract.
    static void validate(JsonNode value, JsonNode schema) {
        String type = schema.path("type").asText();
        require(switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            default -> false;
        }, "AI 출력 타입이 스키마와 다릅니다.");
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode option : schema.path("enum")) found |= option.equals(value);
            require(found, "AI 출력에 허용되지 않은 분류가 있습니다.");
        }
        if (value.isObject()) {
            Set<String> allowed = new HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(allowed::add);
            fields(value, allowed);
            for (JsonNode required : schema.path("required")) {
                require(value.has(required.asText()), "AI 출력 필수 항목이 누락됐습니다.");
            }
            value.fields().forEachRemaining(entry -> validate(entry.getValue(), schema.path("properties").path(entry.getKey())));
        } else if (value.isArray()) {
            require(value.size() >= schema.path("minItems").asInt(0)
                    && value.size() <= schema.path("maxItems").asInt(30), "AI 출력 배열 크기가 올바르지 않습니다.");
            value.forEach(item -> validate(item, schema.path("items")));
        } else {
            require(!value.asText().isBlank() && value.asText().length() <= schema.path("maxLength").asInt(1500),
                    "AI 출력 문자열이 비어 있거나 너무 깁니다.");
        }
    }

    static String hash(JsonNode input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(input).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static JsonNode canonical(JsonNode input) {
        if (input.isObject()) {
            ObjectNode sorted = object();
            List<String> keys = new ArrayList<>();
            input.fieldNames().forEachRemaining(keys::add);
            keys.stream().sorted().forEach(key -> sorted.set(key, canonical(input.get(key))));
            return sorted;
        }
        if (input.isArray()) {
            var values = MAPPER.createArrayNode();
            input.forEach(value -> values.add(canonical(value)));
            return values;
        }
        return input;
    }

    static ObjectNode object() { return MAPPER.createObjectNode(); }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
