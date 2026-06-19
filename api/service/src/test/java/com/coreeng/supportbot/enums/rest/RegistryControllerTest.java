package com.coreeng.supportbot.enums.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistryControllerTest {

    @Mock
    private ImpactsRegistry impactsRegistry;

    @Mock
    private TagsRegistry tagsRegistry;

    @InjectMocks
    private RegistryController controller;

    @Test
    void listImpacts_flagsRetiredAsInactive() {
        when(impactsRegistry.listAllImpacts()).thenReturn(ImmutableList.of(new TicketImpact("Active", "active")));
        when(impactsRegistry.listAllImpactsIncludingRetired())
                .thenReturn(
                        ImmutableList.of(new TicketImpact("Active", "active"), new TicketImpact("Retired", "retired")));

        var body = controller.listImpacts().getBody();

        assertThat(body)
                .containsExactly(
                        new RegistryController.ImpactUI("Active", "active", true),
                        new RegistryController.ImpactUI("Retired", "retired", false));
    }

    @Test
    void listTags_flagsRetiredAsInactive() {
        when(tagsRegistry.listAllTags()).thenReturn(ImmutableList.of(new Tag("Active", "active")));
        when(tagsRegistry.listAllTagsIncludingRetired())
                .thenReturn(ImmutableList.of(new Tag("Active", "active"), new Tag("Retired", "retired")));

        var body = controller.listTags().getBody();

        assertThat(body)
                .containsExactly(
                        new RegistryController.TagUI("Active", "active", true),
                        new RegistryController.TagUI("Retired", "retired", false));
    }
}
