package com.coreeng.supportbot.util;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Jackson mix-in for {@link org.springframework.http.ProblemDetail} matching Spring's own
 * {@code ProblemDetailJacksonMixin}, adapted to {@link JsonMapper}'s field-visibility rules: the
 * {@code properties} field is hidden and its entries are flattened to the top level instead.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
abstract class ProblemDetailMixin {

    @JsonIgnore
    private @Nullable Map<String, Object> properties;

    @JsonAnySetter
    abstract void setProperty(String name, @Nullable Object value);

    @JsonAnyGetter
    abstract Map<String, Object> getProperties();
}
