package com.coreeng.supportbot.testkit.matcher;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import org.jspecify.annotations.NonNull;

public class UrlDecodedPattern extends StringValuePattern {
    @NonNull private final StringValuePattern pattern;

    public UrlDecodedPattern(@NonNull @JsonProperty("matchesUrlDecodedPattern") StringValuePattern pattern) {
        super(pattern.getValue());
        this.pattern = pattern;
    }

    @Override
    public MatchResult match(String value) {
        String decoded = URLDecoder.decode(value, Charset.defaultCharset());
        return pattern.match(decoded);
    }
}
