package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock
    private TicketAssignmentProps assignmentProps;

    @Mock
    private BulkReassignmentService bulkReassignmentService;

    @InjectMocks
    private AssignmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssignmentController(assignmentProps, bulkReassignmentService);
    }

    @Test
    void shouldReturnTrueWhenAssignmentIsEnabled() {
        // given
        when(assignmentProps.enabled()).thenReturn(true);

        // when
        ResponseEntity<AssignmentStatusUI> response = controller.isAssignmentEnabled();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();

        verify(assignmentProps).enabled();
        verifyNoMoreInteractions(assignmentProps);
    }

    @Test
    void shouldReturnFalseWhenAssignmentIsDisabled() {
        // given
        when(assignmentProps.enabled()).thenReturn(false);

        // when
        ResponseEntity<AssignmentStatusUI> response = controller.isAssignmentEnabled();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();

        verify(assignmentProps).enabled();
        verifyNoMoreInteractions(assignmentProps);
    }

    @Test
    void shouldReturn400WhenServiceReturnsValidationError() {
        // given
        BulkReassignRequest request = new BulkReassignRequest(
            List.of(new TicketId(1)),
            "U12345"
        );
        
        BulkReassignResultUI validationError = new BulkReassignResultUI(
            0,
            ImmutableList.of(),
            0,
            ImmutableList.of(),
            "Assignment feature is disabled"
        );
        
        when(bulkReassignmentService.bulkReassign(request)).thenReturn(validationError);

        // when
        ResponseEntity<BulkReassignResultUI> response = controller.bulkReassign(request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(validationError);
        
        verify(bulkReassignmentService).bulkReassign(request);
        verifyNoMoreInteractions(bulkReassignmentService);
    }

    @Test
    void shouldReturn200WhenServiceReturnsSuccessfulResult() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        
        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2),
            "U12345"
        );
        
        BulkReassignResultUI successResult = new BulkReassignResultUI(
            2,
            ImmutableList.of(ticket1, ticket2),
            0,
            ImmutableList.of(),
            "All tickets successfully reassigned"
        );
        
        when(bulkReassignmentService.bulkReassign(request)).thenReturn(successResult);

        // when
        ResponseEntity<BulkReassignResultUI> response = controller.bulkReassign(request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(successResult);
        
        verify(bulkReassignmentService).bulkReassign(request);
        verifyNoMoreInteractions(bulkReassignmentService);
    }
}

