package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

public interface TagsRegistry {
    ImmutableList<Tag> listAllTags();
    ImmutableList<Tag> listTagsByCodes(ImmutableCollection<String> codes);
}
