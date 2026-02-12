package com.coreeng.supportbot.sentiment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private String type;
    private String user;
    private String text;

    @JsonProperty("thread_ts")
    private String threadTs;

    private String ts;

    @Nullable private String team;
}
