package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtGroupTeamMergerTest {

    @Mock
    private TeamService teamService;

    @Test
    void merge_disabled_returnsEmailTeams() {
        var props = new JwtGroupsProperties(false, "groups", List.of());
        var merger = new JwtGroupTeamMerger(props, teamService);
        var emailTeams = ImmutableList.of(tenant("wow"));

        var out = merger.mergeForProvider("dex", Map.of("groups", List.of("developers")), emailTeams);

        assertEquals(emailTeams, out);
    }

    @Test
    void merge_nonDex_returnsEmailTeams() {
        var props = new JwtGroupsProperties(true, "groups", List.of(new JwtGroupsProperties.Mapping(List.of("g"), "wow")));
        var merger = new JwtGroupTeamMerger(props, teamService);
        var emailTeams = ImmutableList.<Team>of();

        var out = merger.mergeForProvider("google", Map.of("groups", List.of("g")), emailTeams);

        assertEquals(emailTeams, out);
    }

    @Test
    void merge_dex_addsMappedTeam() {
        var props = new JwtGroupsProperties(
                true,
                "groups",
                List.of(new JwtGroupsProperties.Mapping(List.of("developers", "Developers"), "wow")));
        var merger = new JwtGroupTeamMerger(props, teamService);
        when(teamService.findTeamByCode("wow")).thenReturn(tenant("wow"));

        var out = merger.mergeForProvider(
                "dex", Map.of("groups", List.of("Developers")), ImmutableList.<Team>of());

        assertEquals(1, out.size());
        assertEquals("wow", out.get(0).code());
    }

    @Test
    void merge_preservesExistingTeams_dedupes() {
        var props = new JwtGroupsProperties(
                true,
                "groups",
                List.of(new JwtGroupsProperties.Mapping(List.of("developers"), "wow")));
        var merger = new JwtGroupTeamMerger(props, teamService);
        var existing = ImmutableList.of(tenant("wow"));
        when(teamService.findTeamByCode("wow")).thenReturn(tenant("wow"));

        var out = merger.mergeForProvider("dex", Map.of("groups", List.of("developers")), existing);

        assertEquals(1, out.size());
    }

    private static Team tenant(String code) {
        return new Team(code, code, ImmutableList.of(TeamType.TENANT));
    }
}
