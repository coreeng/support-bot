package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtGroupsPropertiesTest {

    @Test
    void mapping_typedJwtGroupRef_isAccepted() {
        var mapping = new JwtGroupsProperties.Mapping(new GroupRef.Jwt("developers"), "wow");
        assertThat(mapping.groupRef()).isEqualTo(new GroupRef.Jwt("developers"));
        assertThat(mapping.teamCode()).isEqualTo("wow");
    }

    @Test
    @SuppressWarnings("NullAway")
    void mapping_nullGroupRef_throws() {
        GroupRef nullRef = null;
        assertThatThrownBy(() -> new JwtGroupsProperties.Mapping(nullRef, "wow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must specify 'group-ref'")
                .hasMessageContaining("wow");
    }

    @Test
    void mapping_nonJwtGroupRef_throws() {
        assertThatThrownBy(() -> new JwtGroupsProperties.Mapping(new GroupRef.Slack("S08948NBMED"), "wow"))
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
