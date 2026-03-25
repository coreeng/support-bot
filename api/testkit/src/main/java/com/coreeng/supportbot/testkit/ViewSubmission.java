package com.coreeng.supportbot.testkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.stream.Collectors;

public interface ViewSubmission {
    String triggerId();

    String callbackId();

    String viewType();

    String privateMetadata();

    ImmutableList<Value> values();

    interface Value {
        String name();

        String renderJson();
    }

    record StaticSelectValue(String name, String value) implements Value {
        @Override
        public String renderJson() {
            return String.format("""
                {"type":"single_option","selected_option":{"value":"%s"}}
                """, value);
        }
    }

    record MultiStaticSelectValue(String name, ImmutableList<String> values) implements Value {
        @Override
        public String renderJson() {
            return renderMultiSelectJson("multi_static_select", values);
        }
    }

    record MultiExternalSelectValue(String name, ImmutableList<String> values) implements Value {
        @Override
        public String renderJson() {
            return renderMultiSelectJson("multi_external_select", values);
        }
    }

    private static String renderMultiSelectJson(String type, ImmutableList<String> values) {
        ObjectMapper mapper = new ObjectMapper();
        String selectedOptions = values.stream()
                .map(v -> {
                    try {
                        return "{\"value\":" + mapper.writeValueAsString(v) + "}";
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining(","));
        return String.format("{\"type\":\"%s\",\"selected_options\":[%s]}\n", type, selectedOptions);
    }
}
