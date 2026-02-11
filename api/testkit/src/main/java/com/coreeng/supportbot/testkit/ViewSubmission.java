package com.coreeng.supportbot.testkit;

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
            return String.format(
                    """
                {"type":"multi_static_select","selected_options":[%s]}
                """, values.stream().map(v -> String.format("""
                        {"value":"%s"}
                        """.strip(), v)).collect(Collectors.joining(",")));
        }
    }
}
