package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.EnumerationValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
public class EnumsService implements
    SlackTeamsRegistry,
    TagsRegistry,
    ImpactsRegistry {
    private final EnumProps enumProps;

    @Override
    public ImmutableList<TicketImpact> listAllImpacts() {
        return enumProps.impacts();
    }

    @Nullable
    @Override
    public TicketImpact findImpactByCode(String code) {
        return findByCode(code, enumProps.impacts());
    }

    @Override
    public ImmutableList<SlackTeam> listAllSlackTeams() {
        return enumProps.slackTeams();
    }

    @Nullable
    @Override
    public SlackTeam findSlackTeamByCode(String code) {
        return findByCode(code, enumProps.slackTeams());
    }

    @Nullable
    @Override
    public SlackTeam findSlackTeamById(String id) {
        return enumProps.slackTeams().stream()
            .filter(t -> id.equals(t.id()))
            .findAny()
            .orElse(null);
    }

    @Override
    public ImmutableList<Tag> listAllTags() {
        return enumProps.tags();
    }

    @Override
    public ImmutableList<Tag> listTagsByCodes(ImmutableCollection<String> codes) {
        return enumProps.tags().stream()
            .filter(t -> codes.contains(t.code()))
            .collect(toImmutableList());
    }

    @Nullable
    @Override
    public Tag findTagByCode(String code) {
        return findByCode(code, enumProps.tags());
    }

    @Nullable
    private <T extends EnumerationValue> T findByCode(String code, ImmutableList<T> values) {
        return values.stream()
            .filter(v -> code.equals(v.code()))
            .findAny()
            .orElse(null);
    }
}
