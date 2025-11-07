package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.EnumerationValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class EnumsService implements
    EscalationTeamsRegistry,
    TagsRegistry,
    ImpactsRegistry {
    private final ImpactsRepository impactsRepository;
    private final TagsRepository tagsRepository;
    private final ImmutableList<EscalationTeam> escalationTeams;

    public EnumsService(
        ImpactsRepository impactsRepository,
        TagsRepository tagsRepository,
        EnumProps enumProps
    ) {
        this.impactsRepository = impactsRepository;
        this.tagsRepository = tagsRepository;
        this.escalationTeams = ImmutableList.copyOf(enumProps.escalationTeams());
    }

    @Override
    public ImmutableList<TicketImpact> listAllImpacts() {
        return impactsRepository.listAll();
    }

    @Nullable
    @Override
    public TicketImpact findImpactByCode(String code) {
        return impactsRepository.findImpactByCode(code);
    }

    @Override
    public ImmutableList<Tag> listAllTags() {
        return tagsRepository.listAll();
    }

    @Override
    public ImmutableList<Tag> listTagsByCodes(ImmutableCollection<String> codes) {
        return tagsRepository.listByCodes(codes);
    }

    @Override
    public ImmutableList<EscalationTeam> listAllEscalationTeams() {
        return escalationTeams;
    }

    @Nullable
    @Override
    public EscalationTeam findEscalationTeamByCode(String code) {
        return findByCode(code, escalationTeams);
    }

    @Nullable
    private <T extends EnumerationValue> T findByCode(String code, ImmutableList<T> values) {
        return values.stream()
            .filter(v -> code.equals(v.code()))
            .findAny()
            .orElse(null);
    }
}
