package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Verifies how the optional k8s-generic {@code attributes} map binds into {@link GenericPlatformTeamsFetcher.Config}. */
class GenericPlatformTeamsFetcherConfigBindingTest {

    private static final String CONFIG = "platform-integration.teams-scraping.k8s-generic.config";
    private static final String ATTRIBUTES = CONFIG + ".attributes";

    private static GenericPlatformTeamsFetcher.Config bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bind(CONFIG, GenericPlatformTeamsFetcher.Config.class)
                .get();
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> map = new HashMap<>();
        map.put(CONFIG + ".api-version", "v1alpha1");
        map.put(CONFIG + ".api-group", "example.com");
        map.put(CONFIG + ".kind", "Widget");
        map.put(CONFIG + ".teamName.cel-expression", "resource.metadata.name");
        map.put(CONFIG + ".groupRef.cel-expression", "resource.metadata.uid");
        return map;
    }

    @Test
    void bindsAttributeExpressions() {
        Map<String, Object> map = baseProps();
        map.put(ATTRIBUTES + ".tier.cel-expression", "resource.metadata.?labels.?tier");

        assertThat(bind(map).attributes())
                .containsExactly(Map.entry(
                        "tier", new GenericPlatformTeamsFetcher.CelExpression("resource.metadata.?labels.?tier")));
    }

    @Test
    void bindsAttributeNamesAsWritten_withBracketsForDots() {
        Map<String, Object> map = baseProps();
        map.put(ATTRIBUTES + ".cost-centre.cel-expression", "resource.metadata.?labels.?cost");
        map.put(ATTRIBUTES + ".supportTier.cel-expression", "resource.metadata.?labels.?tier");
        map.put(ATTRIBUTES + "[team.region].cel-expression", "resource.metadata.?labels.?region");

        assertThat(bind(map).attributes()).containsOnlyKeys("cost-centre", "supportTier", "team.region");
    }

    @Test
    void bindsTheRestOfTheConfigAsBefore_whenNoAttributesAreConfigured() {
        GenericPlatformTeamsFetcher.Config config = bind(baseProps());

        assertThat(config.attributes()).isNull();
        assertThat(config.kind()).isEqualTo("Widget");
        assertThat(config.teamName().celExpression()).isEqualTo("resource.metadata.name");
        assertThat(config.groupRef().celExpression()).isEqualTo("resource.metadata.uid");
    }

    @Test
    void failsToBind_whenAttributesAreMalformed() {
        Map<String, Object> map = baseProps();
        map.put(ATTRIBUTES, "tier");

        assertThatThrownBy(() -> bind(map)).isInstanceOf(BindException.class);
    }
}
