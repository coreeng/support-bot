package com.coreeng.supportbot.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsonMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
