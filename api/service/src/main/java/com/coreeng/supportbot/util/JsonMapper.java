package com.coreeng.supportbot.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.datatype.guava.GuavaModule;

@Slf4j
@RequiredArgsConstructor
public class JsonMapper {
    private final tools.jackson.databind.json.JsonMapper objectMapper =
            tools.jackson.databind.json.JsonMapper.builderWithJackson2Defaults()
                    .changeDefaultVisibility(v -> v.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                            .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                            .withVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
                            .withVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY))
                    .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
                    .addModule(new GuavaModule())
                    // This mapper replaces Spring Boot's, which carries this mix-in by default. Without it
                    // the field-visibility rules above serialise ProblemDetail's extension map as a nested
                    // "properties" object instead of flattening it per RFC 9457, so `code` never reaches
                    // the UI.
                    .addMixIn(ProblemDetail.class, ProblemDetailMixin.class)
                    .build();

    @VisibleForTesting
    public tools.jackson.databind.json.JsonMapper getObjectMapper() {
        return objectMapper;
    }

    public String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T fromJsonString(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
