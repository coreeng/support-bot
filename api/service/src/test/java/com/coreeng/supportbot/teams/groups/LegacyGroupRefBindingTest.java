package com.coreeng.supportbot.teams.groups;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.SupportTeamProps;
import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.teams.SupportLeadershipTeamProps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;

/**
 * Proves that pre-PT-351 YAML using {@code slack-group-id} still binds correctly,
 * so existing deployments upgrade without breaking.
 */
class LegacyGroupRefBindingTest {

    @Test
    void supportTeamProps_bindsLegacySlackGroupIdKey() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("team.support.name", "Core Support");
        props.put("team.support.code", "support");
        props.put("team.support.slack-group-id", "S08948NBMED");

        SupportTeamProps bound = bind(props, "team.support", SupportTeamProps.class);

        assertThat(bound.groupRef()).isEqualTo(new GroupRef.Slack("S08948NBMED"));
        assertThat(bound.slackId()).isEqualTo("S08948NBMED");
    }

    @Test
    void supportLeadershipTeamProps_bindsLegacySlackGroupIdKey() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("team.leadership.name", "Support Leadership");
        props.put("team.leadership.code", "support-leadership");
        props.put("team.leadership.slack-group-id", "S01234LEADER");

        SupportLeadershipTeamProps bound = bind(props, "team.leadership", SupportLeadershipTeamProps.class);

        assertThat(bound.groupRef()).isEqualTo(new GroupRef.Slack("S01234LEADER"));
        assertThat(bound.slackId()).isEqualTo("S01234LEADER");
    }

    @Test
    void escalationTeams_bindsLegacySlackGroupIdKey() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("enums.escalation-teams[0].label", "WoW");
        props.put("enums.escalation-teams[0].code", "wow");
        props.put("enums.escalation-teams[0].slack-group-id", "S01234ESCWW");
        props.put("enums.escalation-teams[1].label", "Infra");
        props.put("enums.escalation-teams[1].code", "infra");
        props.put("enums.escalation-teams[1].group-ref", "slack:S01234ESCII");

        List<EscalationTeam> bound = bindList(props, "enums.escalation-teams", EscalationTeam.class);

        assertThat(bound)
                .extracting(EscalationTeam::code, EscalationTeam::groupRef)
                .containsExactly(
                        Assertions.tuple("wow", new GroupRef.Slack("S01234ESCWW")),
                        Assertions.tuple("infra", new GroupRef.Slack("S01234ESCII")));
    }

    private static <T> T bind(Map<String, Object> props, String prefix, Class<T> type) {
        return binder(props).bindOrCreate(prefix, Bindable.of(type));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> bindList(Map<String, Object> props, String prefix, Class<T> elementType) {
        ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, elementType);
        return (List<T>) binder(props).bindOrCreate(prefix, Bindable.of(listType));
    }

    private static Binder binder(Map<String, Object> props) {
        var source = new MapConfigurationPropertySource(props);
        var conversionService = new DefaultFormattingConversionService();
        DefaultConversionService.addDefaultConverters(conversionService);
        conversionService.addConverter(new GroupRefConverter());
        return new Binder(List.of(source), null, conversionService, null, null, null);
    }
}
