package com.coreeng.supportbot.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper()
        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
        .registerModule(new JavaTimeModule())
        .registerModule(new GuavaModule());

    public String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); //NOPMD - suppressed AvoidThrowingRawExceptionTypes - not expected to be caught
        }
    }

    public <T> T fromJsonString(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); //NOPMD - suppressed AvoidThrowingRawExceptionTypes - not expected to be caught
        }
    }
}
