package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.enums.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Verifies that {@code enums.tags} YAML-style properties bind onto {@link Tag} through its canonical
 * constructor — {@link Tag} also has a two-argument convenience constructor, so the bound one has to
 * be picked explicitly — and that the {@code product} flag is optional.
 */
class EnumPropsBindingTest {

    private static List<Tag> bindTags(Map<String, Object> properties) {
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));
        return binder.bind("enums.tags", Bindable.listOf(Tag.class)).get();
    }

    @Test
    void bindsTagsWithAnOptionalProductFlag() {
        Map<String, Object> map = new HashMap<>();
        map.put("enums.tags[0].label", "Networking");
        map.put("enums.tags[0].code", "networking");
        map.put("enums.tags[1].label", "Checkout");
        map.put("enums.tags[1].code", "product-checkout");
        map.put("enums.tags[1].product", "true");
        map.put("enums.tags[2].label", "Legacy");
        map.put("enums.tags[2].code", "legacy");
        map.put("enums.tags[2].product", "false");

        List<Tag> tags = bindTags(map);

        assertThat(tags)
                .containsExactly(
                        new Tag("Networking", "networking", false),
                        new Tag("Checkout", "product-checkout", true),
                        new Tag("Legacy", "legacy", false));
        // The convenience constructor and the bound form agree.
        assertThat(tags.get(0)).isEqualTo(new Tag("Networking", "networking"));
    }
}
