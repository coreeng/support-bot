package com.coreeng.supportbot.teams.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GroupRefTest {

    @Test
    void parse_typedSlack() {
        GroupRef ref = GroupRef.parse("slack:S08948NBMED");
        assertThat(ref).isInstanceOf(GroupRef.Slack.class);
        assertThat(ref.value()).isEqualTo("S08948NBMED");
        assertThat(ref.provider()).isEqualTo(GroupRef.Provider.SLACK);
        assertThat(ref.canonical()).isEqualTo("slack:S08948NBMED");
    }

    @Test
    void parse_typedGoogle() {
        GroupRef ref = GroupRef.parse("google:cloud-eng@corp.com");
        assertThat(ref).isInstanceOf(GroupRef.Google.class);
        assertThat(ref.value()).isEqualTo("cloud-eng@corp.com");
        assertThat(ref.provider()).isEqualTo(GroupRef.Provider.GOOGLE);
    }

    @Test
    void parse_typedAzure() {
        String uuid = "6f0a1b27-1234-4abc-9def-abc123def456";
        GroupRef ref = GroupRef.parse("azure:" + uuid);
        assertThat(ref).isInstanceOf(GroupRef.Azure.class);
        assertThat(ref.value()).isEqualTo(uuid);
    }

    @Test
    void parse_typedJwt() {
        GroupRef ref = GroupRef.parse("jwt:developers");
        assertThat(ref).isInstanceOf(GroupRef.Jwt.class);
        assertThat(ref.value()).isEqualTo("developers");
        assertThat(ref.canonical()).isEqualTo("jwt:developers");
    }

    @Test
    void parse_typedStatic() {
        GroupRef ref = GroupRef.parse("static:wow-group");
        assertThat(ref).isInstanceOf(GroupRef.Static.class);
        assertThat(ref.value()).isEqualTo("wow-group");
    }

    @Test
    void parse_legacyBareSlackId_inferredSlack() {
        GroupRef ref = GroupRef.parse("S08948NBMED");
        assertThat(ref).isInstanceOf(GroupRef.Slack.class);
        assertThat(ref.value()).isEqualTo("S08948NBMED");
    }

    @Test
    void parse_legacyBareUuid_inferredAzure() {
        String uuid = "6f0a1b27-1234-4abc-9def-abc123def456";
        GroupRef ref = GroupRef.parse(uuid);
        assertThat(ref).isInstanceOf(GroupRef.Azure.class);
    }

    @Test
    void parse_legacyEmail_inferredGoogle() {
        GroupRef ref = GroupRef.parse("cloud-eng@corp.com");
        assertThat(ref).isInstanceOf(GroupRef.Google.class);
    }

    @Test
    void parse_legacyBareUnprefixed_fallsBackToStatic() {
        GroupRef ref = GroupRef.parse("wow-group");
        assertThat(ref).isInstanceOf(GroupRef.Static.class);
        assertThat(ref.value()).isEqualTo("wow-group");
    }

    @Test
    void parse_blankInputs_throw() {
        assertThatThrownBy(() -> GroupRef.parse("")).isInstanceOf(GroupRefParseException.class);
        assertThatThrownBy(() -> GroupRef.parse("   ")).isInstanceOf(GroupRefParseException.class);
        assertThatThrownBy(() -> GroupRef.parse(null)).isInstanceOf(GroupRefParseException.class);
    }

    @Test
    void parse_unknownPrefix_throws() {
        assertThatThrownBy(() -> GroupRef.parse("ldap:foo"))
                .isInstanceOf(GroupRefParseException.class)
                .hasMessageContaining("ldap");
    }

    @Test
    void parse_typedForms_roundTripThroughCanonical() {
        for (String form : new String[] {
            "slack:S08948NBMED",
            "google:devs@x.com",
            "azure:6f0a1b27-1234-4abc-9def-abc123def456",
            "jwt:developers",
            "static:wow-group"
        }) {
            GroupRef parsed = GroupRef.parse(form);
            assertThat(parsed.canonical()).isEqualTo(form);
            assertThat(GroupRef.parse(parsed.canonical())).isEqualTo(parsed);
        }
    }

    @Test
    void parse_legacyForms_canonicaliseToTypedPrefix() {
        assertThat(GroupRef.parse("S08948NBMED").canonical()).isEqualTo("slack:S08948NBMED");
        assertThat(GroupRef.parse("wow-group").canonical()).isEqualTo("static:wow-group");
    }

    @Test
    void recordsEqualByValueWithinSameProvider_distinctAcrossProviders() {
        assertThat(new GroupRef.Slack("S0123")).isEqualTo(new GroupRef.Slack("S0123"));
        assertThat((GroupRef) new GroupRef.Slack("S0123")).isNotEqualTo(new GroupRef.Static("S0123"));
    }

    @Test
    void recordCompactConstructor_rejectsBlank() {
        assertThatThrownBy(() -> new GroupRef.Slack("")).isInstanceOf(GroupRefParseException.class);
        assertThatThrownBy(() -> new GroupRef.Jwt("  ")).isInstanceOf(GroupRefParseException.class);
    }
}
