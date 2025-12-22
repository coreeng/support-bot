package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.*;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketUpdateServiceTest {

    @Mock
    private TicketProcessingService ticketProcessingService;

    @Mock
    private TicketQueryService queryService;

    @Mock
    private PlatformTeamsService platformTeamsService;

    @Mock
    private ImpactsRegistry impactsRegistry;

    @Mock
    private TicketUIMapper mapper;

    private TicketUpdateService service;

    private TicketId ticketId;
    private PlatformTeam validTeam;
    private TicketImpact validImpact;

    @BeforeEach
    public void setUp() {
        service = new TicketUpdateService(
            ticketProcessingService,
            queryService,
            impactsRegistry,
            mapper,
            platformTeamsService
        );

        ticketId = new TicketId(123L);
        validTeam = new PlatformTeam("Core Support", java.util.Set.of(), java.util.Set.of());
        validImpact = new TicketImpact("Production Blocking", "production-blocking");
    }

    @Test
    public void shouldUpdateTicketSuccessfully() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug", "urgent"),
            "production-blocking"
        );

        DetailedTicket mockDetailedTicket = mock(DetailedTicket.class);
        TicketUI mockTicketUI = mock(TicketUI.class);

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);
        when(impactsRegistry.findImpactByCode("production-blocking")).thenReturn(validImpact);
        when(ticketProcessingService.submit(any(TicketSubmission.class)))
            .thenReturn(new TicketSubmitResult.Success());
        when(queryService.findDetailedById(ticketId)).thenReturn(mockDetailedTicket);
        when(mapper.mapToUI(any(DetailedTicket.class))).thenReturn(mockTicketUI);

        // when
        TicketUI result = service.update(ticketId, request);

        // then
        assertThat(result).isEqualTo(mockTicketUI);

        ArgumentCaptor<TicketSubmission> submissionCaptor = ArgumentCaptor.forClass(TicketSubmission.class);
        verify(ticketProcessingService).submit(submissionCaptor.capture());

        TicketSubmission capturedSubmission = submissionCaptor.getValue();
        assertThat(capturedSubmission.ticketId()).isEqualTo(ticketId);
        assertThat(capturedSubmission.status()).isEqualTo(TicketStatus.closed);
        assertThat(capturedSubmission.authorsTeam()).isEqualTo(TicketTeam.fromCode("core-support"));
        assertThat(capturedSubmission.tags()).containsExactly("bug", "urgent");
        assertThat(capturedSubmission.impact()).isEqualTo("production-blocking");
        assertThat(capturedSubmission.confirmed()).isTrue();
    }

    @Test
    public void shouldRejectNullRequest() {
        // when/then
        assertThatThrownBy(() -> service.update(ticketId, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Request body is required");

        verifyNoInteractions(ticketProcessingService, queryService, mapper);
    }

    @Test
    public void shouldRejectNullStatus() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            null,
            "core-support",
            ImmutableList.of("bug"),
            "production-blocking"
        );

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("status is required and must be a valid TicketStatus");
    }

    @Test
    public void shouldRejectNullAuthorsTeam() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            null,
            ImmutableList.of("bug"),
            "production-blocking"
        );

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("authorsTeam is required");
    }

    @Test
    public void shouldRejectBlankAuthorsTeam() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "  ",
            ImmutableList.of("bug"),
            "production-blocking"
        );

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("authorsTeam is required");
    }

    @Test
    public void shouldRejectInvalidAuthorsTeam() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "invalid-team",
            ImmutableList.of("bug"),
            "production-blocking"
        );

        when(platformTeamsService.findTeamByName("invalid-team")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("authorsTeam must be a valid team code");

        verify(platformTeamsService).findTeamByName("invalid-team");
    }

    @Test
    public void shouldRejectNullTags() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            null,
            "production-blocking"
        );

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tags is required");
    }

    @Test
    public void shouldRejectEmptyTags() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of(),
            "production-blocking"
        );

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tags must contain at least one value");
    }

    @Test
    public void shouldRejectNullImpact() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug"),
            null
        );

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("impact is required");
    }

    @Test
    public void shouldRejectBlankImpact() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug"),
            "  "
        );

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("impact is required");
    }

    @Test
    public void shouldRejectInvalidImpact() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug"),
            "invalid-impact"
        );

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);
        when(impactsRegistry.findImpactByCode("invalid-impact")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> service.update(ticketId, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("impact must be a valid TicketImpact code");

        verify(impactsRegistry).findImpactByCode("invalid-impact");
    }

    @Test
    public void shouldHandleMultipleTags() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.opened,
            "core-support",
            ImmutableList.of("bug", "urgent", "customer-impacting", "security"),
            "production-blocking"
        );

        DetailedTicket mockDetailedTicket = mock(DetailedTicket.class);
        TicketUI mockTicketUI = mock(TicketUI.class);

        when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);
        when(impactsRegistry.findImpactByCode("production-blocking")).thenReturn(validImpact);
        when(ticketProcessingService.submit(any(TicketSubmission.class)))
            .thenReturn(new TicketSubmitResult.Success());
        when(queryService.findDetailedById(ticketId)).thenReturn(mockDetailedTicket);
        when(mapper.mapToUI(any(DetailedTicket.class))).thenReturn(mockTicketUI);

        // when
        TicketUI result = service.update(ticketId, request);

        // then
        assertThat(result).isEqualTo(mockTicketUI);

        ArgumentCaptor<TicketSubmission> submissionCaptor = ArgumentCaptor.forClass(TicketSubmission.class);
        verify(ticketProcessingService).submit(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().tags())
            .containsExactly("bug", "urgent", "customer-impacting", "security");
    }

    @Test
    public void shouldHandleAllValidTicketStatuses() {
        for (TicketStatus status : TicketStatus.values()) {
            TicketUpdateRequest request = new TicketUpdateRequest(
                status,
                "core-support",
                ImmutableList.of("bug"),
                "production-blocking"
            );

            DetailedTicket mockDetailedTicket = mock(DetailedTicket.class);
            TicketUI mockTicketUI = mock(TicketUI.class);

            when(platformTeamsService.findTeamByName("core-support")).thenReturn(validTeam);
            when(impactsRegistry.findImpactByCode("production-blocking")).thenReturn(validImpact);
            when(ticketProcessingService.submit(any(TicketSubmission.class)))
                .thenReturn(new TicketSubmitResult.Success());
            when(queryService.findDetailedById(ticketId)).thenReturn(mockDetailedTicket);
            when(mapper.mapToUI(any(DetailedTicket.class))).thenReturn(mockTicketUI);

            TicketUI result = service.update(ticketId, request);

            assertThat(result).isEqualTo(mockTicketUI);

            clearInvocations(platformTeamsService, impactsRegistry, ticketProcessingService, queryService, mapper);
        }
    }
}
