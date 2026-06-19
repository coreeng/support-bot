package com.coreeng.supportbot.enums.rest;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/registry")
@RequiredArgsConstructor
public class RegistryController {
    private final ImpactsRegistry impactsRegistry;
    private final TagsRegistry tagsRegistry;

    @GetMapping("/impact")
    public ResponseEntity<List<ImpactUI>> listImpacts() {
        ImmutableSet<String> activeCodes = impactsRegistry.listAllImpacts().stream()
                .map(TicketImpact::code)
                .collect(toImmutableSet());
        ImmutableList<ImpactUI> impacts = impactsRegistry.listAllImpactsIncludingRetired().stream()
                .map(i -> new ImpactUI(i.label(), i.code(), activeCodes.contains(i.code())))
                .collect(toImmutableList());
        return ResponseEntity.ok(impacts);
    }

    @GetMapping("/tag")
    public ResponseEntity<List<TagUI>> listTags() {
        ImmutableSet<String> activeCodes =
                tagsRegistry.listAllTags().stream().map(Tag::code).collect(toImmutableSet());
        ImmutableList<TagUI> tags = tagsRegistry.listAllTagsIncludingRetired().stream()
                .map(t -> new TagUI(t.label(), t.code(), activeCodes.contains(t.code())))
                .collect(toImmutableList());
        return ResponseEntity.ok(tags);
    }

    public record ImpactUI(String label, String code, boolean active) {}

    public record TagUI(String label, String code, boolean active) {}
}
