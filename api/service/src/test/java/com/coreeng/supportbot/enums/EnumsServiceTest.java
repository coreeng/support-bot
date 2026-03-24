package com.coreeng.supportbot.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.EnumProps;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnumsServiceTest {

    @Mock
    private TagsRepository tagsRepository;

    @Mock
    private ImpactsRepository impactsRepository;

    private EnumsService service;

    @BeforeEach
    void setUp() {
        EnumProps enumProps = new EnumProps(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
        service = new EnumsService(impactsRepository, tagsRepository, enumProps);
    }

    @Test
    void listAllTags_delegatesToListAllActive_excludingSoftDeleted() {
        ImmutableList<Tag> activeTags = ImmutableList.of(new Tag("Networking", "networking"));
        when(tagsRepository.listAllActive()).thenReturn(activeTags);

        ImmutableList<Tag> result = service.listAllTags();

        assertThat(result).isEqualTo(activeTags);
        verify(tagsRepository).listAllActive();
    }

    @Test
    void listTagsByCodes_delegatesToListByCodes() {
        ImmutableList<String> codes = ImmutableList.of("networking", "deleted-tag");
        ImmutableList<Tag> tags =
                ImmutableList.of(new Tag("Networking", "networking"), new Tag("Deleted Tag", "deleted-tag"));
        when(tagsRepository.listByCodes(codes)).thenReturn(tags);

        ImmutableList<Tag> result = service.listTagsByCodes(codes);

        assertThat(result).isEqualTo(tags);
        verify(tagsRepository).listByCodes(codes);
    }
}
