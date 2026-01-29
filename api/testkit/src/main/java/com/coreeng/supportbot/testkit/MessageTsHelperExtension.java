package com.coreeng.supportbot.testkit;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;

import java.io.IOException;
import java.util.Map;

    /**
     * WireMock extension that provides a custom Handlebars helper for generating
     * Slack-style message timestamps using {@link MessageTs}.
     *
     * <p>Usage in WireMock response templates:</p>
     * <pre>
     * {{messageTs}}
     * </pre>
     *
     * <p>This generates timestamps like "1737123456.847291" which are unique
     * per call due to the atomic counter in {@link MessageTs#now()}.</p>
     */
public class MessageTsHelperExtension implements TemplateHelperProviderExtension {

    @Override
    public String getName() {
        return "message-ts-helper";
    }

    @Override
    public Map<String, Helper<?>> provideTemplateHelpers() {
        Helper<Object> helper = new Helper<>() {
            @Override
            public Object apply(Object context, Options options) throws IOException {
                return MessageTs.now().toString();
            }
        };
        return Map.of("messageTs", helper);
    }
}

