package com.coreeng.supportbot.teams.groups;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class GroupRefConverter implements Converter<String, GroupRef> {
    @Override
    public GroupRef convert(String source) {
        return GroupRef.parse(source);
    }
}
