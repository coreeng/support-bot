package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
@Order(100)
public class RegistryInitialisation implements ApplicationRunner {
    private final EnumProps enumProps;
    private final TagsRepository tagsRepository;
    private final ImpactsRepository impactsRepository;

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        tagsRepository.deleteAllExcept(
            enumProps.tags().stream()
                .map(Tag::code)
                .collect(toImmutableList())
        );
        tagsRepository.insertOrActivate(enumProps.tags());

        impactsRepository.deleteAllExcept(
            enumProps.impacts().stream()
                .map(TicketImpact::code)
                .collect(toImmutableList())
        );
        impactsRepository.insertOrActivate(enumProps.impacts());
    }
}
