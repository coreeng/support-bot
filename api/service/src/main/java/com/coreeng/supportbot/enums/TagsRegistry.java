package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

public interface TagsRegistry {
    ImmutableList<Tag> listAllTags();

    /** All tags including soft-deleted (retired) — for display/badging, not for pickers (PT-518). */
    ImmutableList<Tag> listAllTagsIncludingRetired();

    ImmutableList<Tag> listTagsByCodes(ImmutableCollection<String> codes);
}
