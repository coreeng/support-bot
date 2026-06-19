package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.EnumerationValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueRetrievalException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EnumsService implements EscalationTeamsRegistry, TagsRegistry, ImpactsRegistry {
    private static final String TAGS_CACHE_KEY = "all-active";

    private final ImpactsRepository impactsRepository;
    private final TagsRepository tagsRepository;
    private final Cache tagsCache;
    private final ImmutableList<EscalationTeam> escalationTeams;

    public EnumsService(
            ImpactsRepository impactsRepository,
            TagsRepository tagsRepository,
            @Qualifier("tags-cache") Cache tagsCache,
            EnumProps enumProps) {
        this.impactsRepository = impactsRepository;
        this.tagsRepository = tagsRepository;
        this.tagsCache = tagsCache;
        this.escalationTeams = ImmutableList.copyOf(enumProps.escalationTeams());
    }

    @Override
    public ImmutableList<TicketImpact> listAllImpacts() {
        return impactsRepository.listAllActive();
    }

    @Override
    public ImmutableList<TicketImpact> listAllImpactsIncludingRetired() {
        return impactsRepository.listAll();
    }

    @Nullable @Override
    public TicketImpact findImpactByCode(String code) {
        return impactsRepository.findImpactByCode(code);
    }

    @Override
    public ImmutableList<Tag> listAllTags() {
        try {
            return Objects.requireNonNull(
                    tagsCache.get(TAGS_CACHE_KEY, tagsRepository::listAllActive),
                    "tags cache returned null for key: " + TAGS_CACHE_KEY);
        } catch (ValueRetrievalException e) {
            // Cache.get(key, Callable) wraps loader exceptions in ValueRetrievalException;
            // unwrap so callers see the original exception.
            log.error("Failed to populate tags cache", e);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause != null) {
                throw new RuntimeException(cause);
            }
            throw new RuntimeException("Cache retrieval failed for key: " + TAGS_CACHE_KEY, e);
        }
    }

    @Override
    public ImmutableList<Tag> listAllTagsIncludingRetired() {
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

    @Nullable @Override
    public EscalationTeam findEscalationTeamByCode(String code) {
        return findByCode(code, escalationTeams);
    }

    @Nullable private <T extends EnumerationValue> T findByCode(String code, ImmutableList<T> values) {
        return values.stream().filter(v -> code.equals(v.code())).findAny().orElse(null);
    }
}
