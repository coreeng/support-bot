package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtGroupsPropertiesTest {

    @Test
    void mapping_typedGroupRef_matchesAgainstClaimValue() {
        var mapping = new JwtGroupsProperties.Mapping(new GroupRef.Jwt("developers"), "wow", List.of());
        assertThat(mapping.matchValues()).containsExactly("developers");
        assertThat(mapping.usingDeprecatedClaimValues()).isFalse();
    }

    @Test
    void mapping_legacyClaimValues_stillSupported_butDeprecated() {
        var mapping = new JwtGroupsProperties.Mapping(List.of("developers", "Developers"), "wow");
        assertThat(mapping.matchValues()).containsExactly("developers", "Developers");
        assertThat(mapping.usingDeprecatedClaimValues()).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway")
    void mapping_neitherSet_throws() {
        GroupRef.Jwt nullRef = null;
        assertThatThrownBy(() -> new JwtGroupsProperties.Mapping(nullRef, "wow", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must specify either")
                .hasMessageContaining("wow");
    }

    @Test
    void mapping_bothSet_throws() {
        assertThatThrownBy(() -> new JwtGroupsProperties.Mapping(
                        new GroupRef.Jwt("developers"), "wow", List.of("developers")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both")
                .hasMessageContaining("wow");
    }

    @Test
    void mapping_nonJwtGroupRef_throws() {
        assertThatThrownBy(() ->
                        new JwtGroupsProperties.Mapping(new GroupRef.Slack("S08948NBMED"), "wow", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use a 'jwt:' prefixed")
                .hasMessageContaining("slack:S08948NBMED");
    }

    @Test
    @SuppressWarnings("NullAway")
    void properties_nullMappings_defaultsToEmpty() {
        List<JwtGroupsProperties.Mapping> nullMappings = null;
        var props = new JwtGroupsProperties(true, "groups", nullMappings);
        assertThat(props.mappings()).isEmpty();
    }

    @Test
    void properties_blankClaimName_defaultsToGroups() {
        var props = new JwtGroupsProperties(true, "  ", List.of());
        assertThat(props.claimName()).isEqualTo("groups");
    }
}
