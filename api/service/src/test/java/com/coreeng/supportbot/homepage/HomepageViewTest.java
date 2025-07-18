package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HomepageViewTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testStateSerialization() {
        HomepageView.State state = HomepageView.State.getDefault();
        String serialized = jsonMapper.toJsonString(state);
        HomepageView.State deserialized = jsonMapper.fromJsonString(serialized, HomepageView.State.class);
        assertEquals(state, deserialized);
    }

    @Test
    public void shouldReturnExpectedTicketsQuery() {
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder()
                        .escalationTeam("team-a")
                        .impact("production blocking")
                        .status(TicketStatus.opened)
                        .tags(ImmutableList.of("tag-a", "tag-b"))
                        .build())
                .build();

        TicketsQuery ticketsQuery = state.toTicketsQuery();

        assertThat(ticketsQuery.impacts().size()).isEqualTo(1);
        assertThat(ticketsQuery.impacts().getFirst()).isEqualTo("production blocking");
        assertThat(ticketsQuery.escalationTeam()).isEqualTo("team-a");
        assertThat(ticketsQuery.tags().size()).isEqualTo(2);
        assertThat(ticketsQuery.tags().get(0)).isEqualTo("tag-a");
        assertThat(ticketsQuery.tags().get(1)).isEqualTo("tag-b");
        assertThat(ticketsQuery.status()).isEqualTo(TicketStatus.opened);
    }
}