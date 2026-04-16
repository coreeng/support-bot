package com.coreeng.supportbot.ticket.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.RatingTicketNotFoundException;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketUpdateService ticketUpdateService;

    @Mock
    private TicketQueryService queryService;

    @Mock
    private TicketUIMapper mapper;

    @Mock
    private TicketTeamSuggestionsService teamSuggestionsService;

    @Mock
    private TicketProcessingService ticketProcessingService;

    @Mock
    private TicketEscalationValidator ticketEscalationValidator;

    @Mock
    private RatingService ratingService;

    private TicketController controller;
    private MockMvc mockMvc;

    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        controller = new TicketController(
                queryService,
                ticketUpdateService,
                ticketProcessingService,
                ticketEscalationValidator,
                ratingService,
                mapper,
                teamSuggestionsService);
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addFormatter(new TicketIdFormatter());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setConversionService(conversionService)
                .build();
        ticketId = new TicketId(123L);
        lenient().when(queryService.findById(ticketId)).thenReturn(mock(Ticket.class));
        lenient()
                .when(ticketEscalationValidator.validate(any(), any()))
                .thenReturn(TicketEscalationValidator.ValidationResult.valid());
    }

    @Test
    void shouldReturnOkWithEmptyBodyWhenRatingSucceeds() throws Exception {
        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {"rating":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(ratingService).save(ticketId, 4);
    }

    @Test
    void shouldReturnNotFoundWhenRatingTicketDoesNotExist() throws Exception {
        doThrow(new RatingTicketNotFoundException(ticketId)).when(ratingService).save(ticketId, 4);

        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {"rating":4}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(ratingService).save(ticketId, 4);
    }

    @Test
    void shouldReturnBadRequestWhenRatingTicketIsOpened() throws Exception {
        doThrow(new IllegalArgumentException("Ticket must be closed before rating can be submitted"))
                .when(ratingService)
                .save(ticketId, 4);

        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {"rating":4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ticket must be closed before rating can be submitted"));

        verify(ratingService).save(ticketId, 4);
    }

    @Test
    void shouldReturnBadRequestWhenRatingAlreadyExists() throws Exception {
        doThrow(new IllegalArgumentException("Ticket has already been rated"))
                .when(ratingService)
                .save(ticketId, 4);

        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {"rating":4}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Ticket has already been rated"));

        verify(ratingService).save(ticketId, 4);
    }

    @Test
    void shouldReturnBadRequestWhenRatingIsInvalid() throws Exception {
        doThrow(new IllegalArgumentException("rating must be between 1 and 5"))
                .when(ratingService)
                .save(ticketId, 0);

        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {"rating":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("rating must be between 1 and 5"));

        verify(ratingService).save(ticketId, 0);
    }

    @Test
    void shouldReturnBadRequestWhenRatingIsMissing() throws Exception {
        mockMvc.perform(post("/ticket/{id}/rating", ticketId.id())
                        .contentType("application/json")
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("rating is required"));

        verifyNoInteractions(ratingService);
    }

    @Test
    void shouldReturnOkWhenUpdateSucceeds() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
                TicketStatus.closed, "core-support", ImmutableList.of("bug", "urgent"), "production-blocking", null);

        TicketUI mockTicketUI = mock(TicketUI.class);
        when(ticketUpdateService.update(ticketId, request)).thenReturn(mockTicketUI);

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockTicketUI);
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() {
        // given
        TicketUpdateRequest request =
                new TicketUpdateRequest(null, "core-support", ImmutableList.of("bug"), "production-blocking", null);

        when(ticketUpdateService.update(ticketId, request))
                .thenThrow(new IllegalArgumentException("status is required"));

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("status is required");
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void shouldReturnBadRequestWhenSubmitFails() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
                TicketStatus.closed, "core-support", ImmutableList.of("bug"), "production-blocking", null);

        when(ticketUpdateService.update(ticketId, request))
                .thenThrow(new IllegalStateException("Update failed: unresolved escalations"));

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("Update failed");
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void shouldEscalateTicketViaProcessingService() {
        // given
        TicketEscalationCreateRequest request =
                new TicketEscalationCreateRequest("core-support", List.of("bug", "urgent"));

        // when
        ResponseEntity<?> response = controller.escalateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(ticketProcessingService)
                .escalate(argThat(escalateRequest -> ticketId.equals(escalateRequest.ticketId())
                        && "core-support".equals(escalateRequest.team())
                        && ImmutableList.of("bug", "urgent").equals(escalateRequest.tags())
                        && escalateRequest.threadPermalink() == null));
    }

    @Test
    void shouldReturnNotFoundWhenEscalationTicketDoesNotExist() {
        // given
        when(queryService.findById(ticketId)).thenReturn(null);

        // when
        ResponseEntity<?> response =
                controller.escalateTicket(ticketId, new TicketEscalationCreateRequest("core-support", List.of()));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(ticketEscalationValidator);
        verify(ticketProcessingService, never()).escalate(any());
    }

    @Test
    void shouldReturnBadRequestWhenEscalationTeamIsMissing() {
        // given
        TicketEscalationCreateRequest request = new TicketEscalationCreateRequest(null, List.of("bug"));
        when(ticketEscalationValidator.validate(request.team(), request.tags()))
                .thenReturn(TicketEscalationValidator.ValidationResult.invalid(
                        TicketEscalationValidator.Field.team, "team is required"));

        // when
        ResponseEntity<?> response = controller.escalateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("team is required");
        verify(ticketProcessingService, never()).escalate(any());
    }

    @Test
    void shouldReturnBadRequestWhenEscalationTeamIsUnknown() {
        // given
        TicketEscalationCreateRequest request = new TicketEscalationCreateRequest("unknown-team", List.of("bug"));
        when(ticketEscalationValidator.validate(request.team(), request.tags()))
                .thenReturn(TicketEscalationValidator.ValidationResult.invalid(
                        TicketEscalationValidator.Field.team, "team must be a valid escalation team"));

        // when
        ResponseEntity<?> response = controller.escalateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("team must be a valid escalation team");
        verify(ticketProcessingService, never()).escalate(any());
    }

    @Test
    void shouldReturnBadRequestWhenEscalationTagsContainNull() {
        // given
        TicketEscalationCreateRequest request =
                new TicketEscalationCreateRequest("core-support", Arrays.asList("bug", null));
        when(ticketEscalationValidator.validate(request.team(), request.tags()))
                .thenReturn(TicketEscalationValidator.ValidationResult.invalid(
                        TicketEscalationValidator.Field.tags, "tags must not contain null"));

        // when
        ResponseEntity<?> response = controller.escalateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("tags must not contain null");
        verify(ticketProcessingService, never()).escalate(any());
    }

    @Test
    void shouldReturnBadRequestWhenEscalationFails() {
        // given
        TicketEscalationCreateRequest request = new TicketEscalationCreateRequest("core-support", List.of());
        doThrow(new IllegalArgumentException("Ticket is closed"))
                .when(ticketProcessingService)
                .escalate(any());

        // when
        ResponseEntity<?> response = controller.escalateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Ticket is closed");
        verify(ticketProcessingService)
                .escalate(argThat(escalateRequest -> ticketId.equals(escalateRequest.ticketId())
                        && "core-support".equals(escalateRequest.team())
                        && ImmutableList.of().equals(escalateRequest.tags())
                        && escalateRequest.threadPermalink() == null));
    }

    @Test
    void listDelegatesToMapperForBatchMapping() {
        // given
        DetailedTicket firstDetailedTicket = detailedTicket(new TicketId(1L));
        DetailedTicket secondDetailedTicket = detailedTicket(new TicketId(2L));
        ImmutableList<DetailedTicket> detailedTickets = ImmutableList.of(firstDetailedTicket, secondDetailedTicket);
        Page<DetailedTicket> detailedTicketsPage = new Page<>(detailedTickets, 0, 1, 2);
        TicketUI firstTicketUi = mock(TicketUI.class);
        TicketUI secondTicketUi = mock(TicketUI.class);
        ImmutableList<TicketUI> mappedTickets = ImmutableList.of(firstTicketUi, secondTicketUi);

        when(queryService.findDetailedTicketByQuery(any())).thenReturn(detailedTicketsPage);
        when(mapper.mapToUIList(detailedTickets)).thenReturn(mappedTickets);

        // when
        ResponseEntity<Page<TicketUI>> response = controller.list(
                0,
                10,
                List.of(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                TicketStatus.opened,
                false,
                List.of(),
                List.of(),
                "");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).containsExactly(firstTicketUi, secondTicketUi);
        verify(mapper).mapToUIList(detailedTickets);
    }

    @Test
    void findByIdDelegatesToMapper() {
        // given
        DetailedTicket detailedTicket = detailedTicket(ticketId);
        TicketUI mappedTicketUI = mock(TicketUI.class);

        when(queryService.findDetailedById(ticketId)).thenReturn(detailedTicket);
        when(queryService.fetchQueryText(detailedTicket.ticket())).thenReturn("Original message");
        when(mapper.mapToUI(detailedTicket, "Original message")).thenReturn(mappedTicketUI);

        // when
        ResponseEntity<TicketUI> response = controller.findById(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mappedTicketUI);
        verify(mapper).mapToUI(detailedTicket, "Original message");
    }

    @Test
    void shouldReturnGroupedTeamSuggestions() {
        // given
        TicketTeamsSuggestion suggestion =
                new TicketTeamsSuggestion(ImmutableList.of("AuthorTeam"), ImmutableList.of("OtherTeam"));
        when(teamSuggestionsService.getTeamSuggestionsForTicket(ticketId)).thenReturn(Optional.of(suggestion));

        // when
        ResponseEntity<TicketTeamSuggestionsUI> response = controller.getTeamSuggestions(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TicketTeamSuggestionsUI body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.suggestedTeams()).containsExactly("AuthorTeam");
        assertThat(body.otherTeams()).containsExactly("OtherTeam");
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() {
        // given
        when(teamSuggestionsService.getTeamSuggestionsForTicket(ticketId)).thenReturn(Optional.empty());

        // when
        ResponseEntity<TicketTeamSuggestionsUI> response = controller.getTeamSuggestions(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static DetailedTicket detailedTicket(TicketId id) {
        Ticket ticket = Ticket.builder()
                .id(id)
                .channelId("C123")
                .queryTs(MessageTs.of("123.456"))
                .createdMessageTs(MessageTs.of("123.457"))
                .status(TicketStatus.opened)
                .team(null)
                .impact("production-blocking")
                .tags(ImmutableList.of())
                .lastInteractedAt(Instant.now())
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();
        return new DetailedTicket(ticket, ImmutableList.of());
    }
}
