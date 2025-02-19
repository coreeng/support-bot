package com.coreeng.supportbot.enums.rest;

import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@RestController
@RequestMapping("/registry")
@RequiredArgsConstructor
public class RegistryController {
    private final ImpactsRegistry impactsRegistry;
    private final TagsRegistry tagsRegistry;

    @GetMapping("/impact")
    public ResponseEntity<List<ImpactUI>> listImpacts() {
        ImmutableList<ImpactUI> impacts = impactsRegistry.listAllImpacts().stream()
            .map(i -> new ImpactUI(
                i.label(),
                i.code()
            ))
            .collect(toImmutableList());
        return ResponseEntity.ok(impacts);
    }

    @GetMapping("/tag")
    public ResponseEntity<List<TagUI>> listTags() {
        ImmutableList<TagUI> tags = tagsRegistry.listAllTags().stream()
            .map(t -> new TagUI(
                t.label(),
                t.code()
            ))
            .collect(toImmutableList());
        return ResponseEntity.ok(tags);
    }


    public record ImpactUI(
        String label,
        String code
    ) {
    }

    public record TagUI(
        String label,
        String code
    ) {
    }
}
