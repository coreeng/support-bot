package com.coreeng.supportbot.enums;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.config.EnumProps;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Order(100)
public class RegistryInitialisation implements ApplicationRunner {
    private final EnumProps enumProps;
    private final TagsRepository tagsRepository;
    private final ImpactsRepository impactsRepository;
    private final EscalationTeamHistoryRepository escalationTeamHistoryRepository;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        tagsRepository.deleteAllExcept(enumProps.tags().stream().map(Tag::code).collect(toImmutableList()));
        tagsRepository.insertOrActivate(enumProps.tags());

        impactsRepository.deleteAllExcept(
                enumProps.impacts().stream().map(TicketImpact::code).collect(toImmutableList()));
        impactsRepository.insertOrActivate(enumProps.impacts());

        // Reconcile escalation team history (code+label) from config so renamed/removed
        // escalation team codes still resolve to a label on existing records (PT-518).
        ImmutableList<EscalationTeam> escalationTeams = ImmutableList.copyOf(enumProps.escalationTeams());
        escalationTeamHistoryRepository.deleteAllExcept(
                escalationTeams.stream().map(EscalationTeam::code).collect(toImmutableList()));
        escalationTeamHistoryRepository.insertOrActivate(escalationTeams);
    }
}
